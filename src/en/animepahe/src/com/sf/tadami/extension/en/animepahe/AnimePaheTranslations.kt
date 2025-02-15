package com.sf.tadami.extension.en.animepahe

import com.sf.tadami.lib.i18n.Language
import com.sf.tadami.lib.i18n.Translations

val AnimePaheTranslations : Translations = mapOf(
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
    "sources_preferences_players_order" to mapOf(
        Language.en to "Server priority",
        Language.fr to "Priorité des serveurs"
    ),
    "sources_preferences_players_order_subtitle" to mapOf(
        Language.en to "Adjust the priority of stream servers to load the streams of your preference",
        Language.fr to "Adaptez la priorité des serveurs de streaming pour charger les streams de votre choix"
    ),
    "sources_preferences_use_hls_links" to mapOf(
        Language.en to "Use HLS links",
        Language.fr to "Utiliser les liens HLS"
    ),
    "sources_preferences_use_hls_links_subtitle" to mapOf(
        Language.en to "Enable this if you are having cloudflare issues",
        Language.fr to "Activez ce paramètre si vous rencontrez des problèmes avec Cloudflare"
    ),
    "search_animes_blank_error_message" to mapOf(
        Language.en to "Search should not be blank for AnimePahe",
        Language.fr to "La recherche ne doit pas être vide pour AnimePahe"
    ),
    "sources_preferences_english_dub" to mapOf(
        Language.en to "Enable english dubs",
        Language.fr to "Activer les sources doublées en anglais"
    ),
    "sources_preferences_english_dub_subtitle" to mapOf(
        Language.en to "Video sources will have an English dub if available",
        Language.fr to "Les sources vidéo auront un doublage en anglais si disponible"
    ),
)
