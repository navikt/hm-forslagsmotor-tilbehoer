package no.nav.hjelpemidler.suggestionengine

internal val querySuggestionsBase =
    """
    SELECT
        sc.hmsnr_hjelpemiddel,
        sc.hmsnr_tilbehoer,
        sum(sc.quantity) AS occurances,
        o.title,
        h.framework_agreement_start,
        h.framework_agreement_end
    FROM v1_score_card AS sc
    LEFT JOIN v1_cache_hmdb AS h ON h.hmsnr = sc.hmsnr_hjelpemiddel
    LEFT JOIN v1_cache_oebs AS o ON o.hmsnr = sc.hmsnr_tilbehoer
    WHERE
        {{WHERE}}
        
        -- Remove illegal accessory hmsnr 000000, it exists only for historical reasons (applies to all products)
        sc.hmsnr_tilbehoer <> '000000'

        -- Remove illegal suggestions from results
        AND (
            SELECT 1 FROM v1_illegal_suggestion WHERE hmsnr_hjelpemiddel = sc.hmsnr_hjelpemiddel AND hmsnr_tilbehoer = sc.hmsnr_tilbehoer
        ) IS NULL

        -- If the product is currently on a framework agreement, only include records with "created" date newer than the
        -- framework_agreement_start-date.
        AND NOT (
            h.framework_agreement_start IS NOT NULL AND
            h.framework_agreement_end IS NOT NULL AND
            (h.framework_agreement_start <= NOW() AND h.framework_agreement_end >= NOW()) AND
            sc.created < h.framework_agreement_start
        )

    GROUP BY sc.hmsnr_hjelpemiddel, sc.hmsnr_tilbehoer, h.framework_agreement_start, h.framework_agreement_end, o.title
    ORDER BY sc.hmsnr_hjelpemiddel, occurances DESC, sc.hmsnr_tilbehoer
    """.trimIndent()

internal val querySuggestions =
    """
    SELECT * FROM (
        ${querySuggestionsBase
        .replace(
            "{{WHERE}}",
            """
                -- Looking for suggestions for a specific product
                sc.hmsnr_hjelpemiddel = ?
                
                -- We do not include results where we do not have a cached title for it (yet)
                AND o.title IS NOT NULL
                
                -- The rest of the where clauses
                AND
            """.trimIndent()
        )}
    ) AS q
    WHERE q.occurances > ?
    LIMIT ?
    ;
    """.trimIndent()

internal val queryIntrospectAllSuggestions =
    """
    SELECT q.*, o_hjelpemiddel.title AS title_hjelpemiddel FROM (
        ${querySuggestionsBase.replace(
        "{{WHERE}}",
        """
            -- We do not include results where we do not have a cached title for it (yet)
            o.title IS NOT NULL
            
            -- The rest of the where clauses
            AND
        """.trimIndent()
    )}
    ) AS q
    LEFT JOIN v1_cache_oebs AS o_hjelpemiddel ON q.hmsnr_hjelpemiddel = o_hjelpemiddel.hmsnr
    WHERE q.occurances > ?
    ;
    """.trimIndent()

internal val queryNumberOfSuggestionsForAllProducts =
    """
    SELECT DISTINCT hmsnr_hjelpemiddel AS hmsnr, count(*) AS suggestions FROM (
        ${querySuggestionsBase.replace("{{WHERE}}", "")}
    ) AS q
    WHERE q.occurances > ?
    GROUP BY hmsnr_hjelpemiddel
    ;
    """.trimIndent()

internal val queryNumberOfSuggestionsWithNoTitleYetForAllProducts =
    """
    SELECT DISTINCT hmsnr_hjelpemiddel AS hmsnr, count(*) AS suggestions FROM (
        ${querySuggestionsBase.replace(
        "{{WHERE}}",
        """
            -- Do not include results where oebs didnt know about the accessory hmsnr
            o.hmsnr IS NOT NULL
           
            -- Lets only look for those with no title yet in the oebs cache
		    AND o.title IS NULL
            
            -- The rest of the where clauses
            AND
        """.trimIndent()
    )}
    ) AS q
    WHERE q.occurances > ?
    GROUP BY hmsnr_hjelpemiddel
    ;
    """.trimIndent()

internal val queryAllSuggestionsForStatsBuilding =
    """
    SELECT
        hmsnr_tilbehoer,
        sum(quantity) AS occurances
    FROM v1_score_card
    WHERE
        hmsnr_hjelpemiddel = ?
        
        -- Remove illegal accessory hmsnr 000000, it exists only for historical reasons (applies to all products)
        AND hmsnr_tilbehoer <> '000000'
        
    GROUP BY hmsnr_tilbehoer
    ORDER BY occurances DESC, hmsnr_tilbehoer
    ;
    """.trimIndent()
