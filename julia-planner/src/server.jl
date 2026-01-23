using HTTP, JSON3, StructTypes, Random, Statistics

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

    # (optional) old stub endpoint
    if r.method == "POST" && r.target == "/plan/next-7-days"
        req = JSON3.read(String(r.body), JuliaPlanRequest)
        resp = plan(req)
        return HTTP.Response(200, JSON3.write(resp);
            headers = ["Content-Type" => "application/json"])
    end

    if r.method == "POST" && r.target == "/plan/score-template"
        req = JSON3.read(String(r.body), ScoreTemplateRequest)
        resp = score_template(req)
        return HTTP.Response(200, JSON3.write(resp); headers=["Content-Type"=>"application/json"])
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

struct Dist
    p10::Float64
    p50::Float64
    p90::Float64
    mean::Float64
    std::Float64
end
StructTypes.StructType(::Type{Dist}) = StructTypes.Struct()

struct ScoreTemplateResponse
    avgUtility::Float64
    tsbDists::Vector{Dist}
end
StructTypes.StructType(::Type{ScoreTemplateResponse}) = StructTypes.Struct()

struct State
    ctl::Float64
    atl::Float64
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

# very simple utility placeholder; swap for your Java logic later
function reward(workout::String, load::Float64)
    workout == "REST_DAY" && return 0.0
    workout == "MOBILITY" && return 3.0
    workout == "GYM_PREHAB" && return 5.0
    return 8.0 + 0.02 * load
end

function score_template(req::ScoreTemplateRequest)::ScoreTemplateResponse
    rng = MersenneTwister(req.seed)
    tsb_samples = [Float64[] for _ in 1:7]

    params = get(USER_MODELS, req.userId, nothing)
    has_model = (params !== nothing)

    total_util = 0.0
    for s in 1:req.sims
        st = State(req.ctl, req.atl)
        util = 0.0

        for i in 1:7
            wt = req.template[i]

            μ::Float64 = 0.0
            σ::Float64 = 0.0

            if wt == "REST_DAY"
                μ = 0.0
                σ = 0.0

            elseif has_model
                k = workout_index(wt)

                # mean from learned baseline * per-type multiplier
                μ = params.b * params.m[k]

                # optional fatigue effect (if you want it)
                β = (req.betaFat === nothing) ? 0.0 : req.betaFat
                fat = clamp(-tsb(st) / 20, 0.0, 2.0)  # 0..2
                μ *= exp(β * fat)

                # IMPORTANT: since you're sampling Normal, treat σ0/σk as *fraction of mean*
                stdFrac = params.σ0 * params.σk[k]
                σ = max(5.0, μ * stdFrac)

            else
                # fallback heuristic
                μ = (wt == "MOBILITY")   ? 8.0  :
                    (wt == "GYM_PREHAB") ? 18.0 : 40.0
                σ = 10.0
            end

            # always apply planner uncertainty last
            σ *= req.baseUncertaintyMult

            load = sample_nonneg_normal(rng, μ, σ)

            st = next_state(st, load)
            push!(tsb_samples[i], tsb(st))

            util += reward(wt, load)
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


# ---------- ForecastState (matches Java exactly) ----------
struct State
    ctl::Float64
    atl::Float64
end

tsb(st::State) = st.ctl - st.atl

function next_state(st::State, dailyLoad::Float64)::State
    nextCtl = st.ctl + (dailyLoad - st.ctl) / 42.0
    nextAtl = st.atl + (dailyLoad - st.atl) / 7.0
    return State(nextCtl, nextAtl)
end

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

function robust_baseline(loads::Vector{Int})
    isempty(loads) && return 40.0
    slice = length(loads) > 28 ? loads[end-27:end] : loads
    sorted = sort(slice)
    n = length(sorted)
    median = isodd(n) ? sorted[(n ÷ 2) + 1] : (sorted[n ÷ 2] + sorted[(n ÷ 2) + 1]) / 2.0
    if median < 10
        nz = [x for x in slice if x > 0]
        meanNonZero = isempty(nz) ? 30.0 : mean(nz)
        return max(20.0, meanNonZero)
    end
    return max(20.0, median)
end

function experience_factor(exp::String, coldStart::Bool)
    !coldStart && return 1.0
    return exp == "BEGINNER" ? 0.75 :
           exp == "CASUAL" ? 0.85 :
           exp == "INTERMEDIATE" ? 0.95 :
           exp == "ADVANCED" ? 1.05 :
           exp == "COMPETITIVE_ATHLETE" ? 1.10 :
           0.90
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

function forecast_load_mean_std(expLevel::String, wt::String, st::State, recentLoads::Vector{Int})
    wt == "REST_DAY" && return (0.0, 0.0)

    baseline = robust_baseline(recentLoads)
    coldStart = count_nonzero(recentLoads) < 5

    mult = workout_multiplier(wt)

    # fatiguePenalty from Java (based on tsb)
    t = tsb(st)
    fatiguePenalty = (t < -20) ? 0.70 : (t < -10) ? 0.85 : 1.0

    expFactor = experience_factor(expLevel, coldStart)

    meanLoad = baseline * mult * fatiguePenalty * expFactor

    baseStdFrac = coldStart ? 0.40 : 0.22
    stdFrac = baseStdFrac + intensity_std_boost(wt)
    stdLoad = max(5.0, meanLoad * stdFrac)

    return (meanLoad, stdLoad)
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

# ---------- Distributions ----------
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

# ---------- Main scoring ----------
function score_template(req::ScoreTemplateRequest)::ScoreTemplateResponse
    rng = MersenneTwister(req.seed)
    tsb_samples = [Float64[] for _ in 1:7]

    # "Do we have a usable learned model passed in from Java?"
    has_model =
        (req.b !== nothing) &&
        (req.m !== nothing) &&
        (req.sigma0 !== nothing) &&
        (req.sigmaK !== nothing) &&
        !isempty(req.m) &&
        !isempty(req.sigmaK)

    total_util = 0.0

    for s in 1:req.sims
        st = State(req.ctl, req.atl)
        util = 0.0

        for i in 1:7
            wt = req.effectiveTemplate[i]   # IMPORTANT: use effectiveTemplate (already injury/readiness/weather mapped)

            μ::Float64 = 0.0
            σ::Float64 = 0.0

            if wt == "REST_DAY"
                μ = 0.0
                σ = 0.0

            elseif has_model
                k = workout_index(wt)  # should be 1..K based on your WORKOUT_ORDER

                # Unknown workout type or mismatched vector lengths -> safe fallback
                if k <= 0 || k > length(req.m) || k > length(req.sigmaK)
                    μ = (wt == "MOBILITY")   ? 8.0  :
                        (wt == "GYM_PREHAB") ? 18.0 : 40.0
                    σ = 10.0
                else
                    μ = req.b * req.m[k]

                    # optional fatigue effect on expected load
                    β = (req.betaFat === nothing) ? 0.0 : req.betaFat
                    fat = clamp(-tsb(st) / 20, 0.0, 2.0)   # 0..2
                    μ *= exp(β * fat)

                    # sigma0/sigmaK are log-space stds (from LogNormal fit)
                    logσ = req.sigma0 * req.sigmaK[k]

                    # convert log-space sigma -> coefficient of variation (std/mean)
                    stdFrac = sqrt(exp(logσ * logσ) - 1.0)

                    if μ <= 0.0
                        μ = 0.0
                        σ = 0.0
                    else
                        σ = max(5.0, μ * stdFrac)
                    end
                end

            else
                # heuristic fallback if no model provided
                μ = (wt == "MOBILITY")   ? 8.0  :
                    (wt == "GYM_PREHAB") ? 18.0 : 40.0
                σ = 10.0
            end

            # always apply planner uncertainty last
            σ *= req.baseUncertaintyMult

            load = (σ <= 0.0) ? 0.0 : sample_nonneg_normal(rng, μ, σ)

            st = next_state(st, load)
            push!(tsb_samples[i], tsb(st))

            # utility + penalties (matches your Java logic)
            util += training_reward(wt, load)
            util -= fatigue_penalty(tsb(st), wt)
            util -= injury_penalty(req.injuryIndex, wt)
            util -= readiness_penalty(req.readiness, wt)
            util -= weather_penalty(req.weatherScores[i], wt)
        end

        total_util += util
    end

    dists = [to_dist(tsb_samples[i]) for i in 1:7]
    return ScoreTemplateResponse(total_util / req.sims, dists)
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
    b::Float64              # baseline load scale
    m::Vector{Float64}      # per-workout multipliers (len K)
    σ0::Float64             # baseline log-noise
    σk::Vector{Float64}     # per-workout log-noise multipliers (len K)
end

# in-memory store keyed by userId
const USER_MODELS = Dict{String, UserModelParams}()

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

    params = UserModelParams(b, m, σ0, σk)
    USER_MODELS[req.userId] = params

    return FitUserModelResponse(true, b, m, σ0, σk, nothing)
end



