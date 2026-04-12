CREATE TABLE IF NOT EXISTS recommendation_cache (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    vacancy_id BIGINT NOT NULL,
    similarity_score DOUBLE PRECISION,
    compatibility_percentage REAL,
    compatibility_level VARCHAR(32),
    feedback TEXT,
    used_llm_feedback BOOLEAN,
    source_profile_updated_at TIMESTAMP,
    source_vacancy_updated_at TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_recommendation_cache_user_vacancy UNIQUE (user_id, vacancy_id)
);

ALTER TABLE recommendation_cache
    ADD COLUMN IF NOT EXISTS source_profile_updated_at TIMESTAMP;

ALTER TABLE recommendation_cache
    ADD COLUMN IF NOT EXISTS source_vacancy_updated_at TIMESTAMP;

ALTER TABLE vacancies
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE vacancies
SET updated_at = created_at
WHERE updated_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_recommendation_cache_updated_at
    ON recommendation_cache (updated_at);

CREATE TABLE IF NOT EXISTS vacancy_swipes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    vacancy_id BIGINT NOT NULL,
    decision VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_vacancy_swipes_user_vacancy UNIQUE (user_id, vacancy_id)
);

CREATE INDEX IF NOT EXISTS idx_vacancy_swipes_user
    ON vacancy_swipes (user_id);
