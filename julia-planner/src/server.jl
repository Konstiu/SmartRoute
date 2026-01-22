using HTTP, JSON3, StructTypes, Random, Statistics

# --- DTOs (keep them boring + JSON-friendly) ---
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

    # NEW: score a template (this is the one your Java chooseBestPlan uses)
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



struct ScoreTemplateRequest
    startDate::String
    template::Vector{String}      # 7 items
    ctl::Float64
    atl::Float64
    recentLoads::Vector{Int}
    injuryIndex::Float64
    readiness::Int
    weatherScores::Vector{Union{Nothing, Float64}}  # allow nulls
    sims::Int
    seed::Int64
    baseUncertaintyMult::Float64
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

    total_util = 0.0
    for s in 1:req.sims
        st = State(req.ctl, req.atl)
        util = 0.0

        for i in 1:7
            wt = req.template[i]

            μ = (wt == "REST_DAY") ? 0.0 :
                (wt == "MOBILITY")  ? 8.0 :
                (wt == "GYM_PREHAB") ? 18.0 : 40.0

            σ = 10.0 * req.baseUncertaintyMult

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

using HTTP, JSON3, StructTypes, Random, Statistics

# ---------- Request / Response ----------
struct ScoreTemplateRequest
    startDate::String
    effectiveTemplate::Vector{String}   # 7 items
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
    total_util = 0.0

    for s in 1:req.sims
        st = State(req.ctl, req.atl)
        util = 0.0

        for i in 1:7
            wt = req.effectiveTemplate[i]

            (μ, σ0) = forecast_load_mean_std(req.experienceLevel, wt, st, req.recentLoads)

            # apply your extra uncertainty multiplier from Java (profile/base + injury/readiness/weather/hard)
            # you already baked injury/readiness/weather into effectiveTemplate, but uncertainty still uses them.
            stdAdj = σ0 * req.baseUncertaintyMult

            load = sample_nonneg_normal(rng, μ, stdAdj)

            st = next_state(st, load)

            t = tsb(st)
            push!(tsb_samples[i], t)

            ws = req.weatherScores[i]
            util += training_reward(wt, load)
            util -= fatigue_penalty(t, wt)
            util -= injury_penalty(req.injuryIndex, wt)
            util -= readiness_penalty(req.readiness, wt)
            util -= weather_penalty(ws, wt)
        end

        total_util += util
    end

    dists = [to_dist(tsb_samples[i]) for i in 1:7]
    return ScoreTemplateResponse(total_util / req.sims, dists)
end

