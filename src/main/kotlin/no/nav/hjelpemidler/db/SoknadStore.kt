package no.nav.hjelpemidler.db

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import mu.KotlinLogging
import java.util.UUID
import javax.sql.DataSource

private val logg = KotlinLogging.logger {}

internal interface SoknadStore {
    fun example(søknadId: UUID): Int
}

internal class SoknadStorePostgres(private val ds: DataSource) : SoknadStore {
    override fun example(søknadId: UUID): Int =
        using(sessionOf(ds)) { session ->
            session.run(
                queryOf(
                    """
                        INSERT INTO v1_soknad (
                            soknads_id
                        ) VALUES (?) ON CONFLICT DO NOTHING
                    """.trimIndent().split("\n").joinToString(" "),
                    søknadId,
                ).asUpdate
            )
        }
}

/*

-- Fetch all products
SELECT DISTINCT(hmsnr_hjelpemiddel) as hmsnr FROM v1_score_card;

-- Framework agreement start date for product
SELECT framework_agreement_start
FROM v1_cache_hmdb
WHERE hmsnr = '014112'
;

-- Fetch suggestions for '014112' newer than 2021-12-01
SELECT * FROM (
	SELECT
		sc.hmsnr_tilbehoer,
		sum(sc.quantity) AS occurances,
		o.title,
		h.framework_agreement_start,
		h.framework_agreement_end
	FROM v1_score_card AS sc
	LEFT JOIN v1_cache_hmdb AS h ON h.hmsnr = sc.hmsnr_hjelpemiddel
	INNER JOIN v1_cache_oebs AS o ON o.hmsnr = sc.hmsnr_tilbehoer
	WHERE
		sc.hmsnr_hjelpemiddel = '014112'

		-- If the product is currently on a framework agreement, only include records with "created" date newer than the
		-- framework_agreement_start-date.
		AND NOT (
			h.framework_agreement_start IS NOT NULL AND
			h.framework_agreement_end IS NOT NULL AND
			(h.framework_agreement_start <= NOW() AND h.framework_agreement_end >= NOW()) AND
			sc.created < h.framework_agreement_start
		)

		-- Remove illegal suggestions from results
		AND (
			SELECT 1 FROM v1_illegal_suggestion WHERE hmsnr_hjelpemiddel = sc.hmsnr_hjelpemiddel AND hmsnr_tilbehoer = sc.hmsnr_tilbehoer
		) IS NULL

	GROUP BY sc.hmsnr_tilbehoer, h.framework_agreement_start, h.framework_agreement_end, o.title
	ORDER BY occurances DESC
) AS q
WHERE q.occurances > 4
;

*/