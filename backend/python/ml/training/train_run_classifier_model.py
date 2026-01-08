import time
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import matplotlib
from sklearn2pmml import sklearn2pmml
from sklearn2pmml.pipeline import PMMLPipeline

from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, confusion_matrix
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report

matplotlib.use('TkAgg')

RUN_TYPE_MAP = {
    "EASY_RUN": 0,
    "TEMPO_RUN": 1,
    "INTERVAL_RUN": 2,
    "LONG_RUN": 3
}

SEX_MAP = {
    "MALE": 0,
    "FEMALE": 1,
    "OTHER": 2
}

EXPERIENCE_MAP = {
    "BEGINNER": 0,
    "CASUAL": 1,
    "INTERMEDIATE": 2,
    "ADVANCED": 3,
    "PROFESSIONAL": 4
}

def train_model(data, test=False, debug_perf=False, export=False):
    df = data
    print(df)

    # map run type string to int
    df["run_type"] = df["run_type"].map(RUN_TYPE_MAP)
    df["sex"] = df["sex"].map(SEX_MAP)
    df["experience_level"] = df["experience_level"].map(EXPERIENCE_MAP)

    X = df.drop(columns=["run_type"])
    y = df["run_type"]

    # split in train and test data
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=.2, random_state=42)

    rf_classifier = RandomForestClassifier(
        n_estimators=250,
        max_depth=10,
        max_features="log2",
        random_state=42,
        n_jobs=-1
    )

    pipeline = PMMLPipeline([
        ("rf", rf_classifier)
    ])

    start = 0
    if debug_perf:
        start = time.perf_counter()

    pipeline.fit(X_train, y_train)

    if debug_perf:
        end = time.perf_counter()
        print(f"Training time: {end - start:.3f} seconds")

    # export to PMML
    if export:
        sklearn2pmml(
            pipeline,
            "run_classifier.pmml",
            with_repr=True
        )

    if test:
        y_pred = pipeline.predict(X_test)
        print(classification_report(y_test, y_pred))

        accuracy = accuracy_score(y_test, y_pred)
        print(f'Accuracy: {accuracy * 100: .2f}%')

        feature_cols = df.drop(columns=["run_type"]).columns
        plt.figure(figsize=(10, 6))
        plt.barh(feature_cols, rf_classifier.feature_importances_)
        plt.xlabel('Feature Importance')
        plt.title('Feature Importance in Random Forest Classifier')
        plt.show()

        conf_matrix = confusion_matrix(y_test, y_pred)
        plt.figure(figsize=(8, 6))
        sns.heatmap(conf_matrix, annot=True, fmt='g', cmap='Blues', cbar=False,
                    xticklabels=["tempo_run", "easy_run", "long_run", "interval_run"],
                    yticklabels=["tempo_run", "easy_run", "long_run", "interval_run"])
        plt.title('Confusion Matrix Heatmap')
        plt.xlabel('Predicted Labels')
        plt.ylabel('True Labels')
        plt.show()


df_runs = pd.read_csv("datasets/RunDataset.csv")


train_model(df_runs, test=True, debug_perf=True, export=True)
