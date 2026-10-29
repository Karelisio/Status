package com.karelisio.status.tweaks

data class BloatEntry(val pkg: String, val name: String, val note: String, val safe: Boolean = true)

/**
 * Preinstalled packages known to be safe to disable on Honor / MagicOS phones (plus common
 * partner apps). "safe = false" entries are listed for information but trigger a warning.
 * Only packages actually present on the device are shown.
 */
object BloatList {
    val entries: List<BloatEntry> = listOf(
        // Honor / MagicOS
        BloatEntry("com.hihonor.gamecenter", "Centre de jeux Honor", "Boutique et assistant de jeux Honor"),
        BloatEntry("com.hihonor.vmall", "Honor Store", "Boutique en ligne Honor"),
        BloatEntry("com.hihonor.phoneservice", "My Honor", "Support et services Honor"),
        BloatEntry("com.hihonor.tips", "Astuces", "Conseils d'utilisation"),
        BloatEntry("com.hihonor.browser", "Navigateur Honor", "Navigateur préinstallé"),
        BloatEntry("com.hihonor.email", "E-mail Honor", "Client mail préinstallé"),
        BloatEntry("com.hihonor.fastapp", "Applis rapides", "Mini-applis sans installation"),
        BloatEntry("com.hihonor.health", "Santé Honor", "Inutile sans montre ou bracelet Honor"),
        BloatEntry("com.hihonor.wallet", "Portefeuille Honor", "Paiement Honor (indisponible en Europe)"),
        BloatEntry("com.hihonor.kidsmode", "Mode enfant", "Espace enfant"),
        BloatEntry("com.hihonor.parentcontrol", "Contrôle parental", "Contrôle parental Honor"),
        BloatEntry("com.hihonor.magicvoice", "Assistant vocal YOYO", "Assistant vocal Honor"),
        BloatEntry("com.hihonor.intelligent", "Suggestions YOYO", "Écran -1 et suggestions"),
        BloatEntry("com.hihonor.search", "Recherche globale", "Recherche Honor (balayage vers le bas)"),
        BloatEntry("com.hihonor.appmarket", "App Market", "Store Honor : ses applis ne seront plus mises à jour"),
        BloatEntry("com.hihonor.hnoffice", "Honor Office", "Suite bureautique"),
        BloatEntry("com.hihonor.notepad", "Bloc-notes Honor", "Si vous utilisez une autre appli de notes"),
        BloatEntry("com.hihonor.findmydevice", "Localiser mon appareil", "Sécurité antivol : à garder", safe = false),
        BloatEntry("com.hihonor.android.thememanager", "Thèmes", "Nécessaire aux fonds d'écran et thèmes", safe = false),
        BloatEntry("com.hihonor.android.pushagent", "Service push Honor", "Notifications des applis Honor", safe = false),
        BloatEntry("com.hihonor.systemmanager", "Gestionnaire du téléphone", "Composant système critique", safe = false),
        // Huawei-era packages still found on some Honor builds
        BloatEntry("com.huawei.gameassistant", "Assistant de jeu", "Superposition de jeu"),
        BloatEntry("com.huawei.hifolder", "Dossiers suggérés", "Recommandations d'applis"),
        BloatEntry("com.huawei.tips", "Astuces Huawei", "Conseils d'utilisation"),
        // Partners
        BloatEntry("com.facebook.appmanager", "Facebook App Manager", "Mises à jour Facebook en arrière-plan"),
        BloatEntry("com.facebook.services", "Facebook Services", "Services Facebook préinstallés"),
        BloatEntry("com.facebook.system", "Facebook App Installer", "Installateur Facebook"),
        BloatEntry("com.facebook.katana", "Facebook", "Appli préinstallée"),
        BloatEntry("com.booking", "Booking.com", "Appli partenaire"),
        BloatEntry("com.netflix.partner.activation", "Netflix activation", "Service partenaire Netflix"),
        BloatEntry("com.netflix.mediaclient", "Netflix", "Appli partenaire"),
        BloatEntry("com.amazon.appmanager", "Amazon App Manager", "Service partenaire Amazon"),
        BloatEntry("com.amazon.mShop.android.shopping", "Amazon Shopping", "Appli partenaire"),
        BloatEntry("com.zhiliaoapp.musically", "TikTok", "Appli partenaire"),
        BloatEntry("com.einnovation.temu", "Temu", "Appli partenaire"),
        BloatEntry("com.alibaba.aliexpresshd", "AliExpress", "Appli partenaire"),
        BloatEntry("com.linkedin.android", "LinkedIn", "Appli partenaire"),
        BloatEntry("com.ebay.mobile", "eBay", "Appli partenaire"),
        BloatEntry("com.tripadvisor.tripadvisor", "Tripadvisor", "Appli partenaire"),
        BloatEntry("com.agoda.mobile.consumer", "Agoda", "Appli partenaire"),
        BloatEntry("com.king.candycrushsaga", "Candy Crush", "Jeu préinstallé"),
        BloatEntry("com.block.juggle", "Block Blast", "Jeu préinstallé"),
        // Google optional
        BloatEntry("com.google.android.apps.tachyon", "Google Meet", "Visio Google"),
        BloatEntry("com.google.android.videos", "Google TV", "Films et séries Google"),
        BloatEntry("com.google.android.apps.youtube.music", "YouTube Music", "Si vous utilisez un autre lecteur"),
        BloatEntry("com.google.android.apps.subscriptions.red", "Google One", "Abonnement Google"),
        BloatEntry("com.google.android.apps.wellbeing", "Bien-être numérique", "Utilisé par certains réglages système", safe = false),
    )

    private val byPkg = entries.associateBy { it.pkg }

    fun lookup(pkg: String): BloatEntry? = byPkg[pkg]

    /** Packages that must never be offered for disabling. */
    val critical = setOf(
        "android", "com.android.systemui", "com.android.phone", "com.android.settings",
        "com.android.providers.settings", "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.android.shell",
        "com.hihonor.android.launcher", "com.android.launcher3", "com.google.android.gms", "com.android.vending",
        "com.android.inputmethod.latin", "com.google.android.inputmethod.latin", "com.hihonor.systemserver",
    )
}
