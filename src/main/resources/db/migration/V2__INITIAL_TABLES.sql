-- All applications we have seen in case we need a future reference
CREATE TABLE v1_soknad (
    soknads_id          UUID        NOT NULL,
    data                JSONB       NOT NULL,
    created             TIMESTAMP   NOT NULL DEFAULT (NOW()),
    PRIMARY KEY (soknads_id)
);

CREATE INDEX v1_soknad_created_index ON v1_soknad (created DESC);

-- This table contains the metadata needed to generate suggestions
CREATE TABLE v1_score_card (
    hmsnr_hjelpemiddel  TEXT        NOT NULL,
    hmsnr_tilbehoer     TEXT        NOT NULL,
    soknads_id          UUID        NOT NULL,
    quantity            INTEGER     NOT NULL,
    created             TIMESTAMP   NOT NULL,
    PRIMARY KEY (hmsnr_hjelpemiddel, hmsnr_tilbehoer, soknads_id)
);

CREATE INDEX v1_score_card_created_index ON v1_score_card (created);

-- Cache for OEBS name lookups (products and accessories)
CREATE TABLE v1_cache_oebs (
    hmsnr               TEXT        NOT NULL,
    title               TEXT        NULL,
    type                TEXT        NULL,
    cached_at           TIMESTAMP   NOT NULL DEFAULT (NOW()),
    PRIMARY KEY (hmsnr)
);

CREATE INDEX v1_cache_oebs_type_index ON v1_cache_oebs (type);

-- Cache for HMDB lookup of framework agreement start/end for products
CREATE TABLE v1_cache_hmdb (
   hmsnr                        TEXT        NOT NULL,
   framework_agreement_start    DATE	   	NULL,
   framework_agreement_end      DATE   		NULL,
   cached_at                    TIMESTAMP   NOT NULL DEFAULT (NOW()),
   PRIMARY KEY (hmsnr)
);

CREATE INDEX v1_cache_hmdb_type_index ON v1_cache_hmdb (framework_agreement_start, framework_agreement_end);

-- Illegal suggestions to be filtered out of suggestion results
CREATE TABLE v1_illegal_suggestion (
    hmsnr_hjelpemiddel          TEXT        NOT NULL,
    hmsnr_tilbehoer             TEXT        NOT NULL,
    created                     TIMESTAMP   NOT NULL DEFAULT (NOW()),
    PRIMARY KEY (hmsnr_hjelpemiddel, hmsnr_tilbehoer)
);
