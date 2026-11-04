"""Replay one confirmed dead-letter event. Credentials come only from RABBITMQ_* environment variables."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python backend/scripts/notification_replay.py <eventId>")
    event_id = str(uuid.UUID(sys.argv[1]))
    for name in ("RABBITMQ_USER", "RABBITMQ_PASSWORD"):
        if not os.environ.get(name):
            raise SystemExit(f"Environment variable required: {name}")
    backend = Path(__file__).resolve().parents[1]
    classpath_file = backend / "target" / "notification-classpath.txt"
    maven = shutil.which("mvn.cmd" if os.name == "nt" else "mvn")
    if not maven:
        raise SystemExit("Maven is required on PATH")
    subprocess.run([maven, "-q", "-DskipTests", "compile", "dependency:build-classpath",
                    "-Dmdep.outputFile=target/notification-classpath.txt"], cwd=backend, check=True)
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else shutil.which("java")
    if not java:
        raise SystemExit("Java 21 is required")
    classpath = str(backend / "target" / "classes") + os.pathsep + classpath_file.read_text().strip()
    subprocess.run([java, "-cp", classpath,
                    "com.campuslink.module.notification.infrastructure.messaging.NotificationReplayMain", event_id],
                   cwd=backend, check=True)


if __name__ == "__main__":
    main()
