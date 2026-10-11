package com.sf.tadami.extension.fr.franime

import com.sf.tadami.lib.i18n.Language
import com.sf.tadami.lib.i18n.Translations

val FrAnimeTranslations : Translations = mapOf(
    "category_network" to mapOf(
        Language.en to "Network",
        Language.fr to "Réseau"
    ),
    "category_player" to mapOf(
        Language.en to "Player",
        Language.fr to "Lecteur Vidéo"
    ),
    "sources_preferences_base_url" to mapOf(
        Language.en to "Override the base url",
        Language.fr to "Remplacer l\'url de base"
    ),
    "sources_preferences_base_url_subtitle" to mapOf(
        Language.en to "If you want to modify the source base address in case it is not working already.",
        Language.fr to "Si vous souhaitez modifier l\'adresse de base de la source si celle-ci ne fonctionne plus."
    ),
    "sources_preferences_user_agent" to mapOf(
        Language.en to "Override the user agent",
        Language.fr to "Remplacer le user agent de base"
    ),
    "sources_preferences_user_agent_subtitle" to mapOf(
        Language.en to "If you want to modify the source user agent in case it is not working already. Set it to \"default\" to use the app's one.",
        Language.fr to "Si vous souhaitez modifier le user agent de base de la source si celle-ci ne fonctionne plus. Mettez \"default\" pour utiliser celui de l'application."
    ),
    "sources_preferences_players_order" to mapOf(
        Language.en to "Server priority",
        Language.fr to "Priorité des serveurs"
    ),
    "sources_preferences_players_order_subtitle" to mapOf(
        Language.en to "Adjust the priority of stream servers to load the streams of your preference",
        Language.fr to "Adaptez la priorité des serveurs de streaming pour charger les streams de votre choix"
    ),
    "source_episode_tooltip_content" to mapOf(
        Language.en to """
            #### If you don't have sources

            FrAnime limits how many requests can be made in a short time:
            - Wait a few seconds and reload the episode
            - If a server keeps failing, move it down in the server priority of the source settings
           """.trimIndent(),
        Language.fr to """
            #### Si vous n'avez pas de sources

            FrAnime limite le nombre de requêtes sur une courte période :
            - Attendez quelques secondes et rechargez l'épisode
            - Si un serveur échoue souvent, descendez-le dans la priorité des serveurs des paramètres de la source
            """.trimIndent()
    )
)
