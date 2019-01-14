{
    "package": {
        "name": "distributions",
        "repo": "yorc-a4c-plugin",
        "subject": "ystia",
        "desc": "Yorc Alien4Cloud Plugin Repository",
        "website_url": "https://ystia.github.io/",
        "issue_tracker_url": "https://github.com/ystia/yorc-a4c-plugin/issues",
        "vcs_url": "https://github.com/ystia/yorc-a4c-plugin",
        "github_use_tag_release_notes": false,
        "github_release_notes_file": "CHANGELOG.md",
        "licenses": ["Apache-2.0"],
        "labels": [],
        "public_download_numbers": true,
        "public_stats": false,
        "attributes": []
    },

    "version": {
        "name": "${VERSION_NAME}",
        "desc": "Yorc Alien4Cloud Plugin Repository ${VERSION_NAME}",
        "released": "${RELEASE_DATE}",
        "vcs_tag": "${TAG_NAME}",
        "attributes": [],
        "gpgSign": false
    },

    "files":
        [
        {"includePattern": "distribution/target/(alien4cloud-yorc-plugin-distribution-.*\\.zip)", "uploadPattern": "$1"},
        {"includePattern": "alien4cloud-yorc-plugin/target/(alien4cloud-yorc-plugin-.*\\.zip)", "uploadPattern": "$1"}
        ],
    "publish": true
}

