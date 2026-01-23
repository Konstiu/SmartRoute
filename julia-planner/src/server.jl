using HTTP, JSON3, StructTypes, Random, Statistics, Turing, Distributions, MCMCChains

# --- DTOs (keep them boring + JSON-friendly) ---
const WORKOUT_ORDER = [
  "EASY_RUN",
  "TEMPO_RUN",
  "INTERVAL_RUN",
  "LONG_RUN",
  "GYM_PREHAB",
  "MOBILITY",
  "REST_DAY"
]
const WT_TO_IDX = Dict(w => i for (i, w) in enumerate(WORKOUT_ORDER))

workout_index(wt::String) = get(WT_TO_IDX, wt, WT_TO_IDX["REST_DAY"])
const K = length(WORKOUT_ORDER)

struct PlanRequest
    userId::Int
    startDate::String
    readiness::Int
    injuryIndex::Float64
    recentLoads::Vector{Int}
    seed::Int
end
StructTypes.StructType(::Type{PlanRequest}) = StructTypes.Struct()

struct DayPrediction
    dayIndex::Int
    workoutType::String
    loadMean::Float64
    loadStd::Float64
end
StructTypes.StructType(::Type{DayPrediction}) = StructTypes.Struct()

struct PlanResponse
    score::Float64
    days::Vector{DayPrediction}
end
StructTypes.StructType(::Type{PlanResponse}) = StructTypes.Struct()

# --- stub: replace with your probabilistic logic later ---
function plan(req::PlanRequest)::PlanResponse
    # deterministic via seed if you want later: Random.seed!(req.seed)
    days = DayPrediction[]
    for d in 0:6
        # dummy outputs
        push!(days, DayPrediction(d, d == 2 ? "INTERVAL_RUN" : "EASY_RUN", 40.0, 10.0))
    end
    return PlanResponse(123.4, days)
end

function handler(r::HTTP.Request)
    # simple health check
    if r.method == "GET" && r.target == "/health"
        return HTTP.Response(200, "ok")
    end

    if r.method == "POST" && r.target == "/model/fit-user"
        req = JSON3.read(String(r.body), FitUserModelRequest)
        resp = fit_user_model(req)
        return HTTP.Response(200, JSON3.write(resp); headers=["Content-Type"=>"application/json"])
    end

    if r.method == "POST" && r.target == "/plan/score-template"
        req = JSON3.read(String(r.body), ScoreTemplateRequest)
        resp = score_template(req)
        return HTTP.Response(200, JSON3.write(resp);
            headers = ["Content-Type" => "application/json"])
    end

    return HTTP.Response(404, "Not found")
end

HTTP.serve(handler, "0.0.0.0", 8081)

struct Dist
    p10::Float64
    p50::Float64
    p90::Float64
    mean::Float64
    std::Float64
end
StructTypes.StructType(::Type{Dist}) = StructTypes.Struct()

# ---------- Request / Response ----------
struct ScoreTemplateRequest
    userId::String
    startDate::String
    template::Vector{String}
    effectiveTemplate::Vector{String}
    ctl::Float64
    atl::Float64
    recentLoads::Vector{Int}
    experienceLevel::String
    injuryIndex::Float64
    readiness::Int
    weatherScores::Vector{Union{Nothing, Float64}}
    sims::Int
    seed::Int64
    baseUncertaintyMult::Float64

    b::Union{Nothing, Float64}
    m::Union{Nothing, Vector{Float64}}
    sigma0::Union{Nothing, Float64}
    sigmaK::Union{Nothing, Vector{Float64}}
    betaFat::Union{Nothing, Float64}
end
StructTypes.StructType(::Type{ScoreTemplateRequest}) = StructTypes.Struct()

struct ScoreTemplateResponse
    avgUtility::Float64
    tsbDists::Vector{Dist}
end
StructTypes.StructType(::Type{ScoreTemplateResponse}) = StructTypes.Struct()

struct State
    ctl::Float64
    atl::Float64
end

struct PosteriorDraw
    b::Float64
    m::Vector{Float64}
    σ0::Float64
    σk::Vector{Float64}
    βfat::Float64
end


tsb(st::State) = st.ctl - st.atl

function next_state(st::State, dailyLoad::Float64)::State
    nextCtl = st.ctl + (dailyLoad - st.ctl) / 42.0
    nextAtl = st.atl + (dailyLoad - st.atl) / 7.0
    return State(nextCtl, nextAtl)
end

sample_nonneg_normal(rng, μ, σ) = max(0.0, μ + σ * randn(rng))

function quantile_sorted(v::Vector{Float64}, q::Float64)
    n = length(v)
    n == 1 && return v[1]
    pos = q * (n - 1)
    lo = floor(Int, pos) + 1
    hi = ceil(Int, pos) + 1
    lo == hi && return v[lo]
    w = pos - floor(pos)
    return v[lo] * (1 - w) + v[hi] * w
end

function to_dist(samples::Vector{Float64})
    sort!(samples)
    m = mean(samples)
    s = length(samples) >= 2 ? std(samples) : 0.0
    return Dist(
        quantile_sorted(samples, 0.10),
        quantile_sorted(samples, 0.50),
        quantile_sorted(samples, 0.90),
        m, s
    )
end

function score_template(req::ScoreTemplateRequest)::ScoreTemplateResponse
    rng = MersenneTwister(req.seed)
    tsb_samples = [Float64[] for _ in 1:7]

    draws = get(USER_POST, req.userId, nothing)

    total_util = 0.0
    for s in 1:req.sims
        # --- choose params per sim (posterior predictive) ---
        params::Union{Nothing,PosteriorDraw} = nothing

        if draws !== nothing && !isempty(draws)
            params = draws[rand(rng, 1:length(draws))]
        elseif req.b !== nothing && req.m !== nothing && req.sigma0 !== nothing && req.sigmaK !== nothing
            β = (req.betaFat === nothing) ? 0.0 : req.betaFat
            params = PosteriorDraw(req.b, req.m, req.sigma0, req.sigmaK, β)
        end

        has_model = (params !== nothing)

        st = State(req.ctl, req.atl)
        util = 0.0

        for i in 1:7
            base = req.template[i]
            items, wts = map_probs_softmax(base, req.readiness, req.injuryIndex, req.weatherScores[i])
            wt = sample_categorical(rng, items, wts)

            μ = 0.0
            σ = 0.0

            if wt == "REST_DAY"
                μ = 0.0
                σ = 0.0

            elseif has_model
                k = workout_index(wt)

                # safe guard: unknown workout or bad vector lengths => fallback heuristic
                if k < 1 || k > length(params.m) || k > length(params.σk)
                    μ = (wt == "MOBILITY")   ? 8.0  :
                        (wt == "GYM_PREHAB") ? 18.0 : 40.0
                    σ = 10.0
                else
                    # --- model-consistent LogNormal sampling ---
                    μ_adj = params.b * params.m[k]
                    fat = clamp(-tsb(st) / 20, 0.0, 2.0)
                    μ_adj *= exp(params.βfat * fat)

                    # log-space sigma from fitted model
                    logσ = params.σ0 * params.σk[k]

                    # apply planner uncertainty in log-space (simple + effective)
                    logσ *= req.baseUncertaintyMult

                    # sample load (always positive)
                    load = (μ_adj <= 0.0) ? 0.0 : rand(rng, LogNormal(log(max(1e-3, μ_adj)), logσ))

                    # (optional) set μ, σ for debugging only
                    μ = μ_adj
                    σ = 0.0
                end

            else
                μ = 40.0
                stdFrac = 0.25
                logσ = sqrt(log(1 + stdFrac^2))
                load = rand(rng, LogNormal(log(μ), logσ))
            end

            st = next_state(st, load)
            push!(tsb_samples[i], tsb(st))

            # use your full utility if you want; keeping your existing reward call here
            util += training_reward(wt, load)
            # optionally subtract penalties using req.injuryIndex / req.readiness / req.weatherScores[i]
        end

        total_util += util
    end

    dists = [to_dist(tsb_samples[i]) for i in 1:7]
    return ScoreTemplateResponse(total_util / req.sims, dists)
end

has_model(req) =
    req.b !== nothing &&
    req.m !== nothing &&
    req.sigma0 !== nothing &&
    req.sigmaK !== nothing

# ---------- Sampling (Box–Muller like your Java) ----------
function sample_nonneg_normal(rng::AbstractRNG, mean::Float64, std::Float64)
    std <= 0 && return max(0.0, mean)
    u1 = max(1e-12, rand(rng))
    u2 = rand(rng)
    z = sqrt(-2.0 * log(u1)) * cos(2 * pi * u2)
    return max(0.0, mean + std * z)
end

# ---------- Forecaster port (from your LoadForecasterService) ----------
function count_nonzero(loads::Vector{Int})
    c = 0
    for x in loads
        x > 0 && (c += 1)
    end
    return c
end

function workout_multiplier(wt::String)
    return wt == "REST_DAY" ? 0.0 :
           wt == "MOBILITY" ? 0.15 :
           wt == "GYM_PREHAB" ? 0.35 :
           wt == "EASY_RUN" ? 0.90 :
           wt == "TEMPO_RUN" ? 1.25 :
           wt == "INTERVAL_RUN" ? 1.45 :
           wt == "LONG_RUN" ? 1.60 :
           0.90
end

function intensity_std_boost(wt::String)
    return wt == "INTERVAL_RUN" ? 0.10 :
           wt == "TEMPO_RUN" ? 0.06 :
           wt == "LONG_RUN" ? 0.08 :
           0.0
end

# ---------- Your penalties/reward (ported from TrainingPlan7dServiceImpl) ----------
function training_reward(wt::String, loadSample::Float64)
    wt == "REST_DAY" && return 0.0
    wt == "MOBILITY" && return 3.0
    wt == "GYM_PREHAB" && return 5.0
    return 8.0 + 0.02 * loadSample
end

function fatigue_penalty(tsbVal::Float64, wt::String)
    p = 0.0
    if tsbVal < -25
        p += 25
    elseif tsbVal < -15
        p += 12
    elseif tsbVal < -10
        p += 6
    end
    hard = (wt == "INTERVAL_RUN" || wt == "TEMPO_RUN" || wt == "LONG_RUN")
    if hard && tsbVal < -10
        p += 6
    end
    return p
end

function injury_penalty(injuryIndex::Float64, wt::String)
    injuryIndex < 0.4 && return 0.0
    hard = (wt == "INTERVAL_RUN" || wt == "TEMPO_RUN" || wt == "LONG_RUN")
    p = 0.0
    if injuryIndex >= 0.7
        hard && (p += 30)
        wt == "EASY_RUN" && (p += 8)
    else
        wt == "INTERVAL_RUN" && (p += 18)
        wt == "TEMPO_RUN" && (p += 8)
    end
    return p
end

function readiness_penalty(readiness::Int, wt::String)
    readiness >= 70 && return 0.0
    hard = (wt == "INTERVAL_RUN" || wt == "TEMPO_RUN" || wt == "LONG_RUN")
    if readiness < 40
        hard && return 25.0
        wt == "EASY_RUN" && return 8.0
        return 0.0
    end
    if hard
        return 10.0
    end
    if wt == "EASY_RUN" && readiness < 55
        return 3.0
    end
    return 0.0
end

function weather_penalty(weatherScore::Union{Nothing,Float64}, wt::String)
    weatherScore === nothing && return 0.6
    outdoorRun = (wt == "EASY_RUN" || wt == "TEMPO_RUN" || wt == "INTERVAL_RUN" || wt == "LONG_RUN")
    !outdoorRun && return 0.0
    weatherScore >= 0.7 && return 0.0
    weatherScore >= 0.5 && return 2.0
    weatherScore >= 0.3 && return 8.0
    return 18.0
end

@model function load_model(y, wt, tsb, K)
    # baseline scale
    b ~ LogNormal(log(40.0), 0.6)

    # per-type multipliers (positive, around 1)
    m ~ filldist(LogNormal(0.0, 0.35), K)

    # noise terms
    σ0 ~ LogNormal(log(0.25), 0.5)                 # log-space noise scale
    σk ~ filldist(LogNormal(log(1.0), 0.25), K)

    # fatigue effect on expected load (optional but useful)
    β_fat ~ Normal(0.0, 0.25)

    for t in eachindex(y)
        μ = b * m[wt[t]]

        # tsb is roughly [-40, 40]; convert to "fatigue" 0..~2
        fat = clamp(-tsb[t] / 20, 0.0, 2.0)
        μ_adj = μ * exp(β_fat * fat)

        σ = σ0 * σk[wt[t]]

        y[t] ~ LogNormal(log(max(1e-3, μ_adj)), σ)
    end
end


# ---------- Incoming obs ----------
struct PplDailyObs
    date::String
    workoutType::String
    totalLoad::Int
    distanceMeters::Float64
    movingTimeSeconds::Int
    elevationGainMeters::Float64
    weatherScore::Union{Nothing, Float64}
    tsb::Union{Nothing, Float64}
end
StructTypes.StructType(::Type{PplDailyObs}) = StructTypes.Struct()

struct FitUserModelRequest
    userId::String
    experienceLevel::String
    days::Vector{PplDailyObs}
    ctl0::Float64
    atl0::Float64
    seed::Int64
end
StructTypes.StructType(::Type{FitUserModelRequest}) = StructTypes.Struct()

# ---------- Stored params ----------
struct UserModelParams
    b::Float64
    m::Vector{Float64}
    σ0::Float64
    σk::Vector{Float64}
    βfat::Float64
end

# in-memory store keyed by userId
const USER_POST = Dict{String, Vector{PosteriorDraw}}()
const USER_MODELS = Dict{String, PosteriorDraw}()  # optional point-estimate for convenience


struct FitUserModelResponse
    ok::Bool
    b::Float64
    m::Vector{Float64}
    sigma0::Float64
    sigmaK::Vector{Float64}
    betaFat::Union{Nothing, Float64}
end
StructTypes.StructType(::Type{FitUserModelResponse}) = StructTypes.Struct()

function fit_user_model(req::FitUserModelRequest)::FitUserModelResponse
    rng = MersenneTwister(req.seed)

    # ---------- build dataset ----------
    y = Float64[]
    wt = Int[]
    tsb_raw = Union{Nothing,Float64}[]

    for d in req.days
        k = workout_index(d.workoutType)
        k == WT_TO_IDX["REST_DAY"] && continue

        load = float(max(0, d.totalLoad))
        load <= 0 && continue

        push!(y, load)
        push!(wt, k)
        push!(tsb_raw, d.tsb)
    end

    # ---------- cold start: heuristic fallback ----------
    if length(y) < 12
        params = fit_user_model_heuristic(req)  # MUST return UserModelParams(b,m,σ0,σk,βfat)
        USER_MODELS[req.userId] = params
        USER_POST[req.userId] = [params]        # trivial posterior
        return FitUserModelResponse(true, params.b, params.m, params.σ0, params.σk, params.βfat)
    end

    # ---------- align / compute tsb ----------
    tsb_vec = Float64[]
    if any(x === nothing for x in tsb_raw)
        st = State(req.ctl0, req.atl0)
        for i in eachindex(y)
            st = next_state(st, y[i])
            push!(tsb_vec, tsb(st))
        end
    else
        tsb_vec = Float64[Float64(x) for x in tsb_raw]
    end

    # ---------- fit with Turing ----------
    model = load_model(y, wt, tsb_vec, K)

    n_adapt   = 400
    n_samples = 600
    chain = sample(rng, model, NUTS(0.65), n_samples; nadapts=n_adapt)

    # ---------- point estimate (posterior means) ----------
    b_hat  = clamp(mean(chain[:b]), 10.0, 200.0)
    σ0_hat = clamp(mean(chain[:σ0]), 0.05, 1.0)
    β_hat  = mean(chain[:β_fat])

    m_hat  = vec(mean(Array(chain[:m]), dims=1))
    σk_hat = vec(mean(Array(chain[:σk]), dims=1))

    for k in 1:K
        m_hat[k]  = clamp(m_hat[k], 0.1, 4.0)
        σk_hat[k] = clamp(σk_hat[k], 0.3, 3.0)
    end
    m_hat[WT_TO_IDX["REST_DAY"]] = 0.0

    point = UserModelParams(b_hat, m_hat, σ0_hat, σk_hat, β_hat)
    USER_MODELS[req.userId] = point

    # ---------- posterior draws for posterior predictive scoring ----------
    # Extract vectors/matrices robustly
    b_vec  = vec(Array(chain[:b]))
    σ0_vec = vec(Array(chain[:σ0]))
    β_vec  = vec(Array(chain[:β_fat]))

    m_mat  = Array(chain[:m])    # could be (N,K) or (K,N) depending on extraction
    σk_mat = Array(chain[:σk])

    N = length(b_vec)

    # normalize to (N, K)
    if size(m_mat, 1) == K && size(m_mat, 2) == N
        m_mat = permutedims(m_mat)
    end
    if size(σk_mat, 1) == K && size(σk_mat, 2) == N
        σk_mat = permutedims(σk_mat)
    end

    ndraws = min(40, N)
    idxs = rand(rng, 1:N, ndraws)

    draws = Vector{UserModelParams}(undef, ndraws)
    for (j, t) in enumerate(idxs)
        b  = clamp(b_vec[t], 10.0, 200.0)
        σ0 = clamp(σ0_vec[t], 0.05, 1.0)
        β  = β_vec[t]

        m  = vec(m_mat[t, :])
        σk = vec(σk_mat[t, :])

        for k in 1:K
            m[k]  = clamp(m[k], 0.1, 4.0)
            σk[k] = clamp(σk[k], 0.3, 3.0)
        end
        m[WT_TO_IDX["REST_DAY"]] = 0.0

        draws[j] = UserModelParams(b, m, σ0, σk, β)
    end

    USER_POST[req.userId] = draws

    return FitUserModelResponse(true, point.b, point.m, point.σ0, point.σk, point.βfat)
end


function fit_user_model_heuristic(req::FitUserModelRequest)::UserModelParams
    # collect loads per category index
    loads_by_k = [Float64[] for _ in 1:K]

    for d in req.days
        y = float(max(0, d.totalLoad))
        y <= 0 && continue

        k = workout_index(d.workoutType)
        k == WT_TO_IDX["REST_DAY"] && continue
        push!(loads_by_k[k], y)
    end

    # choose baseline b from EASY_RUN if present, else overall median
    function geom_mean(v)
        isempty(v) && return NaN
        lv = log.(max.(v, 1.0))
        return exp(mean(lv))
    end

    easy_k = WT_TO_IDX["EASY_RUN"]
    b = geom_mean(loads_by_k[easy_k])

    if !isfinite(b)
        # fallback: median of all non-rest loads
        allv = reduce(vcat, loads_by_k; init=Float64[])
        b = isempty(allv) ? 40.0 : median(allv)
    end
    b = max(10.0, b)

    # multipliers
    m = ones(Float64, K)
    for k in 1:K
        gm = geom_mean(loads_by_k[k])
        if isfinite(gm)
            m[k] = clamp(gm / b, 0.2, 3.0)
        end
    end
    m[WT_TO_IDX["REST_DAY"]] = 0.0

    # log-noise estimates
    # σ0 = typical log std, σk are relative multipliers
    logstds = fill(NaN, K)
    for k in 1:K
        v = loads_by_k[k]
        if length(v) >= 4
            resid = log.(max.(v, 1.0)) .- log(max(1e-3, b*m[k]))
            logstds[k] = std(resid)
        end
    end

    # baseline σ0 from median of available types
    avail = [s for s in logstds if isfinite(s)]
    σ0 = isempty(avail) ? 0.25 : median(avail)
    σ0 = clamp(σ0, 0.08, 0.60)

    σk = ones(Float64, K)
    for k in 1:K
        if isfinite(logstds[k])
            σk[k] = clamp(logstds[k] / σ0, 0.6, 1.8)
        end
    end
    σk[WT_TO_IDX["REST_DAY"]] = 1.0

    params = UserModelParams(b, m, σ0, σk, 0.0)
    USER_MODELS[req.userId] = params

    return params
end

sigmoid(x) = 1 / (1 + exp(-x))

function clamp01(x)
    return min(1.0, max(0.0, x))
end

# Pick a workout from candidates using weights
function sample_categorical(rng, items::Vector{String}, w::Vector{Float64})
    s = sum(w)
    if s <= 0
        return items[end] # fallback
    end
    u = rand(rng) * s
    acc = 0.0
    for (it, wi) in zip(items, w)
        acc += wi
        if u <= acc
            return it
        end
    end
    return items[end]
end

softmax(x) = (ex = exp.(x .- maximum(x)); ex ./ sum(ex))

function map_probs_softmax(base, readiness, injury, weather)
    r = clamp01((70 - readiness)/40)
    inj = clamp01((injury - 0.4)/0.4)
    w = weather === nothing ? 0.35 : clamp01((0.7 - weather)/0.7)
    pressure = clamp01(0.55*r + 0.35*inj + 0.20*w)

    # candidates + "difficulty"
    if base == "INTERVAL_RUN"
        items = ["INTERVAL_RUN","TEMPO_RUN","EASY_RUN","REST_DAY"]
        diff  = [3.0, 2.2, 1.0, 0.0]
    elseif base == "TEMPO_RUN"
        items = ["TEMPO_RUN","EASY_RUN","REST_DAY"]
        diff  = [2.2, 1.0, 0.0]
    elseif base == "LONG_RUN"
        items = ["LONG_RUN","EASY_RUN","REST_DAY"]
        diff  = [2.5, 1.0, 0.0]
    else
        return [base], [1.0]
    end

    # pressure increases penalty on difficulty
    λ = 0.8 + 2.0*pressure
    logits = .-(λ .* diff)
    p = softmax(logits)
    return items, collect(p)
end






