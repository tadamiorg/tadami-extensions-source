package com.sf.tadami.extension.fr.ianimes

import com.sf.tadami.lib.i18n.Language
import com.sf.tadami.lib.i18n.Translations

val IAnimesTranslations: Translations = mapOf(
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
        Language.fr to "Remplacer l'url de base"
    ),
    "sources_preferences_base_url_subtitle" to mapOf(
        Language.en to "If you want to modify the source base address in case it is not working already.",
        Language.fr to "Si vous souhaitez modifier l'adresse de base de la source si celle-ci ne fonctionne plus."
    ),
    "sources_preferences_players_order" to mapOf(
        Language.en to "Server priority",
        Language.fr to "Priorité des serveurs"
    ),
    "sources_preferences_players_order_subtitle" to mapOf(
        Language.en to "Adjust the priority of stream servers to load the streams of your preference",
        Language.fr to "Adaptez la priorité des serveurs de streaming pour charger les streams de votre choix"
    ),
    "filters_independent" to mapOf(
        Language.en to "The genre filter is ignored when searching by text",
        Language.fr to "Le filtre de genre est ignoré lors d'une recherche par texte"
    ),
    "filters_genre" to mapOf(
        Language.en to "Genre",
        Language.fr to "Genre"
    ),
    "filters_genre_all" to mapOf(
        Language.en to "All (A-Z)",
        Language.fr to "Tous (A-Z)"
    ),
    "episode_film" to mapOf(
        Language.en to "Movie",
        Language.fr to "Film"
    ),
    "episode_name" to mapOf(
        Language.en to "Episode %s",
        Language.fr to "Épisode %s"
    ),
    "episode_season_name" to mapOf(
        Language.en to "Season %s - Episode %s",
        Language.fr to "Saison %s - Épisode %s"
    ),
    "error_no_players" to mapOf(
        Language.en to "No video player found on this episode page",
        Language.fr to "Aucun lecteur vidéo trouvé sur la page de cet épisode"
    ),
    "error_no_streams" to mapOf(
        Language.en to "None of the video servers of this episode could be read",
        Language.fr to "Aucun des serveurs vidéo de cet épisode n'a pu être lu"
    ),
)
