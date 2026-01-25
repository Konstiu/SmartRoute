# Run Classification Model Training

### Purpose

This Radnom Forest model classifies running activities into four types 
(easy run, long run, interval run, tempo run) based on physiological, performance, and environmental data 
to provide personalized training insights.

### Setup

1. Start the backend with the ``datagen_train`` profile.
2. Locate the training data file in ``target/RunDataset.csv`` and move it into ``python/ml/training/datasets``
3. Setup the python venv (described in ``python/Readme.md``) and execute ``pip install -r requirements.txt``
4. Execute the classifier: ``python train_run_classifier_model.py``
5. Move the trained model file ``run_classifier.pmml`` to ``src/main/resources/models``

### Model Features

| Column                    | Description                                                                                                    |
|---------------------------|----------------------------------------------------------------------------------------------------------------|
| `duration`                | Duration of the run in seconds.                                                                                |
| `duration_pct_pb_20`      | Duration as a percentage of the user’s personal best over the last 20 runs.                                    |
| `distance`                | Distance of the run in meters.                                                                                 |
| `distance_pct_pb_20`      | Distance as a percentage of the user’s personal best over the last 20 runs.                                    |
| `pace`                    | Average pace in meters per second.                                                                             |
| `pace_pct_pb_20`          | Pace as a percentage of the user’s personal best over the last 20 runs.                                        |
| `elevation_gain`          | Total elevation gain in meters.                                                                                |
| `session_load`            | Computed session load value (>= 0).                                                                            |
| `num_pace_spikes`         | Number of significant pace spikes during the run.                                                              |
| `num_pace_spikes_missing` | Flag indicating if `num_pace_spikes` is missing (0 = present, 1 = missing).                                    |
| `readiness_score`         | Readiness score of the user prior to the run (0-100).                                                          |           
| `consistency_score`       | Consistency score based on user’s training history (0-1).                                                      |
| `tsb`                     | Training Stress Balance of the user.                                                                           |
| `age`                     | Age of the user in years.                                                                                      |
| `weight`                  | Weight of the user in kilograms.                                                                               |
| `height`                  | Height of the user in centimeters.                                                                             |
| `sex`                     | Sex of the user (`Male`, `Female` or `Other`).                                                                 |
| `experience_level`        | Experience level of the user (e.g., `beginner`, `casual`, `intermediate`, `advanced`, `professional_athlete`). |
| `injury_index`            | Injury index score (numeric) indicating injury risk or history.                                                |
| `hr_avg`                  | Average heart rate during the run (% of user’s max HR).                                                        |
| `hr_avg_missing`          | Flag indicating if `hr_avg` is missing (0 = present, 1 = missing).                                             |
| `hr_max`                  | Maximum heart rate during the run (% of user’s max HR).                                                        |
| `hr_max_missing`          | Flag indicating if `hr_max` is missing (0 = present, 1 = missing).                                             |
| `zone1`                   | Time spent in HR zone 1 (in seconds).                                                                          |
| `zone1pct`                | Percentage of total time spent in HR zone 1.                                                                   |
| `zone1_missing`           | Flag indicating if `zone1` is missing (0 = present, 1 = missing).                                              |
| `zone2`                   | Time spent in HR zone 2 (in seconds).                                                                          |
| `zone2pct`                | Percentage of total time spent in HR zone 2.                                                                   |
| `zone2_missing`           | Flag indicating if `zone2` is missing (0 = present, 1 = missing).                                              |
| `zone3`                   | Time spent in HR zone 3 (in seconds).                                                                          |
| `zone3pct`                | Percentage of total time spent in HR zone 3.                                                                   |
| `zone3_missing`           | Flag indicating if `zone3` is missing (0 = present, 1 = missing).                                              |
| `zone4`                   | Time spent in HR zone 4 (in seconds).                                                                          |
| `zone4pct`                | Percentage of total time spent in HR zone 4.                                                                   |
| `zone4_missing`           | Flag indicating if `zone4` is missing (0 = present, 1 = missing).                                              |
| `zone5`                   | Time spent in HR zone 5 (in seconds).                                                                          |
| `zone5pct`                | Percentage of total time spent in HR zone 5.                                                                   |
| `zone5_missing`           | Flag indicating if `zone5` is missing (0 = present, 1 = missing).                                              |
| `num_hr_spikes`           | Number of significant heart rate spikes during the run.                                                        |
| `num_hr_spikes_missing`   | Flag indicating if `num_hr_spikes` is missing.                                                                 |
| `windSpeed10m`            | Wind speed in km/h during the run.                                                                             |
| `temperature2m`           | Temperature in °C during the run.                                                                              |
| `uv_index`                | UV index during the run (0–13).                                                                                |
| `precipitation`           | Precipitation during the run in mm/h.                                                                          |
| `snowDepth`               | Snow depth in cm during the run.                                                                               |
| `run_type`                | Target label of the run (integer: 0 = easy_run, 1 = long_run, 2 = interval_run, 3 = tempo_run).                |