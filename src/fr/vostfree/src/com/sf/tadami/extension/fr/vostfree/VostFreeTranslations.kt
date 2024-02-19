package com.sf.tadami.extension.fr.vostfree

import com.sf.tadami.lib.i18n.Language
import com.sf.tadami.lib.i18n.Translations

val VostFreeTranslations : Translations = mapOf(
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
    "discover_search_screen_filters_group_selected_text" to mapOf(
        Language.en to "select",
        Language.fr to "selectionner"
    ),
    "discover_search_filters_independent" to mapOf(
        Language.en to "Filters ignores each other",
        Language.fr to "Les filtres et la recherche s'ignorent"
    ),
    "vostfree_search_length_error" to mapOf(
        Language.en to "Search query must be at least 4 caracters long",
        Language.fr to "La recherche doit faire au moins 4 caractères"
    ),
    "sources_preferences_players_order" to mapOf(
        Language.en to "Server priority",
        Language.fr to "Priorité des serveurs"
    ),
    "sources_preferences_players_order_subtitle" to mapOf(
        Language.en to "Adjust the priority of stream servers to load the streams of your preference",
        Language.fr to "Adaptez la priorité des serveurs de streaming pour charger les streams de votre choix"
    ),

)
