CREATE TABLE IF NOT EXISTS app_updates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    version_code INTEGER NOT NULL,
    version_name TEXT NOT NULL,
    download_url TEXT NOT NULL,
    release_notes TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT OR IGNORE INTO app_updates (id, version_code, version_name, download_url, release_notes)
VALUES (1, 1, 'v1.0.0', 'https://drive.google.com/drive/folders/19GIJWEZPmPr-IO5W2CxugXzX8rkAjLyq?usp=sharing', 'Initial Release');
