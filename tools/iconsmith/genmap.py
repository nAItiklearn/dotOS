"""Generates icon_map.json from grouped package lists; validates glyph ids and uniqueness."""
import os
import json
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "feature", "icons", "src", "main", "assets", "icon_map.json")

M = {}
dups = []


def add(glyph, *pkgs):
    for p in pkgs:
        p = p.strip()
        if not p:
            continue
        if p in M and M[p] != glyph:
            dups.append((p, M[p], glyph))
        M[p] = glyph


# ------------------------------------------------------------------ phone / contacts / messaging
add("phone",
    "com.google.android.dialer", "com.android.dialer", "com.android.phone", "com.android.incallui",
    "com.android.server.telecom", "com.samsung.android.dialer", "com.samsung.android.incallui",
    "com.samsung.android.app.telephonyui", "com.simplemobiletools.dialer", "org.fossify.phone",
    "com.lge.incallui", "com.google.android.apps.googlevoice")
add("contacts",
    "com.google.android.contacts", "com.android.contacts", "com.samsung.android.app.contacts",
    "com.samsung.android.contacts", "com.simplemobiletools.contacts.pro", "org.fossify.contacts")
add("sim",
    "com.android.stk", "com.android.stk2", "com.miui.virtualsim", "com.coloros.simsettings",
    "com.google.android.apps.tycho", "com.airalo.app")
add("messages",
    "com.google.android.apps.messaging", "com.android.mms", "com.android.messaging",
    "com.samsung.android.messaging", "com.simplemobiletools.smsmessenger", "org.fossify.messages",
    "com.moez.QKSMS", "com.textra", "com.jb.gosms", "com.handcent.nextsms", "xyz.klinker.messenger",
    "com.asus.message", "com.lge.message", "com.htc.sense.mms", "com.sonyericsson.conversations")
add("chat_alt",
    "com.google.android.apps.dynamite", "com.google.android.talk", "im.vector.app", "chat.simplex.app")
add("video_call",
    "us.zoom.videomeetings", "com.microsoft.teams", "com.skype.raider", "com.google.android.apps.meetings",
    "com.google.android.apps.tachyon", "com.cisco.webex.meetings", "org.jitsi.meet")
add("mail",
    "com.google.android.gm", "com.google.android.gm.lite", "com.google.android.apps.inbox", "com.android.email",
    "com.samsung.android.email.provider", "com.microsoft.office.outlook", "ch.protonmail.android",
    "com.fsck.k9", "net.thunderbird.android", "eu.faircode.email", "com.yahoo.mobile.client.android.mail",
    "com.my.mail", "ru.yandex.mail", "de.tutao.tutanota", "com.readdle.spark", "com.easilydo.mail",
    "com.zoho.mail", "com.ninefolders.hd3", "com.asus.email", "com.lge.email",
    "com.htc.android.mail", "com.sonyericsson.email", "com.cloudmagic.mail")
add("feedback", "com.samsung.android.voc", "com.miui.bugreport", "com.google.android.feedback")
add("help",
    "com.motorola.help", "com.motorola.motocare", "com.samsung.android.app.tips", "com.android.emergency",
    "com.miui.miservice", "com.huawei.phoneservice", "com.hihonor.phoneservice")

# ------------------------------------------------------------------ camera / gallery / media
add("camera",
    "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.sec.android.app.camera",
    "com.motorola.camera3", "com.motorola.camera5", "com.motorola.camera", "com.oneplus.camera",
    "com.oplus.camera", "com.oppo.camera", "com.coloros.camera", "com.nothing.camera", "com.huawei.camera",
    "com.asus.camera", "com.simplemobiletools.camera", "org.fossify.camera", "net.sourceforge.opencamera",
    "com.sonyericsson.android.camera", "com.lge.camera", "com.htc.camera", "com.mediatek.camera",
    "com.meizu.media.camera", "org.lineageos.snap", "org.codeaurora.snapcam")
add("camera_alt",
    "com.google.ar.lens", "com.samsung.android.visionintelligence", "com.samsung.android.arzone")
add("gallery",
    "com.google.android.apps.photos", "com.google.android.apps.photosgo", "com.android.gallery3d",
    "com.android.gallery", "com.sec.android.gallery3d", "com.miui.gallery", "com.coloros.gallery3d",
    "com.oneplus.gallery", "com.vivo.gallery", "com.huawei.photos",
    "com.simplemobiletools.gallery.pro", "org.fossify.gallery", "com.amazon.clouddrive.photos",
    "com.miui.mediaeditor", "com.google.android.apps.wallpaper", "com.miui.android.fashiongallery",
    "com.asus.gallery", "com.sonyericsson.album", "com.htc.album")
add("video",
    "com.google.android.videos", "com.miui.video", "com.coloros.video", "com.samsung.android.video",
    "com.huawei.himovie.overseas", "com.miui.screenrecorder", "com.coloros.screenrecorder",
    "com.hecorat.screenrecorder.free", "com.kimcy929.screenrecorder", "com.duapps.recorder")
add("stream",
    "org.videolan.vlc", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro", "com.miui.videoplayer",
    "is.xyz.mpv", "com.brouken.player", "com.bsplayer.bspandroid.free", "org.schabi.newpipe",
    "com.github.libretube")
add("music",
    "com.google.android.music", "com.google.android.apps.youtube.music", "com.android.music",
    "com.sec.android.app.music", "com.miui.player", "com.heytap.music", "com.oppo.music",
    "com.maxmpz.audioplayer", "com.neutroncode.mp", "com.simplemobiletools.musicplayer",
    "org.fossify.musicplayer", "code.name.monkey.retromusic", "ch.blinkenlights.android.vanilla",
    "com.poupa.vinylmusicplayer", "com.sonos.acr", "com.sonos.acr2", "com.asus.music",
    "com.htc.music", "com.lge.music", "com.sonyericsson.music", "com.huawei.music")
add("podcast",
    "au.com.shiftyjelly.pocketcasts", "com.bambuna.podcastaddict", "fm.castbox.audiobook.radio.podcast",
    "de.danoeh.antennapod", "com.google.android.apps.podcasts",
    "com.google.android.apps.wearables.maestro.companion", "com.sony.songpal.mdr", "com.oplus.melody")
add("radio",
    "com.android.fmradio", "com.caf.fmradio", "com.mediatek.fmradio", "com.sec.android.app.fm", "com.miui.fm",
    "com.motorola.android.fmradio", "tunein.player", "com.streema.simpleradio", "com.radio.fmradio",
    "net.programmierecke.radiodroid2")
add("recorder",
    "com.google.android.apps.recorder", "com.sec.android.app.voicenote", "com.android.soundrecorder",
    "com.oneplus.soundrecorder", "com.coloros.soundrecorder", "com.oplus.soundrecorder",
    "com.android.bbksoundrecorder", "com.simplemobiletools.voicerecorder", "org.fossify.voicerecorder",
    "com.asus.soundrecorder", "com.nll.acr", "com.dimowner.audiorecorder",
    "com.coffeebeanventures.easyvoicerecorder")
add("voice", "com.google.android.tts", "com.samsung.SMT", "com.google.android.apps.accessibility.voiceaccess")
add("tv",
    "org.xbmc.kodi", "org.jellyfin.mobile", "com.samsung.android.tvplus", "com.samsung.android.smartmirroring",
    "com.roku.remote", "com.amazon.storm.lightning.client.aosp", "com.peel.app")
add("game",
    "com.google.android.play.games", "com.samsung.android.game.gamehome", "com.samsung.android.game.gametools",
    "com.xiaomi.gamecenter", "com.nearme.gamecenter", "com.vivo.game", "com.oplus.games", "com.asus.armourycrate",
    "com.valvesoftware.android.steam.community", "com.epicgames.portal", "com.nvidia.geforcenow",
    "com.nvidia.tegrazone3", "com.playstation.remoteplay", "com.playstation.mobile2ndscreen", "com.nintendo.znca")
add("book",
    "com.google.android.apps.books", "com.amazon.kindle", "com.kobobooks.android",
    "org.geometerplus.zlibrary.ui.android", "com.faultexception.reader", "com.flyersoft.moonreader",
    "com.flyersoft.moonreaderp", "org.readera", "com.audible.application", "com.google.android.apps.pdfviewer",
    "com.adobe.reader", "com.xodo.pdf.reader", "com.foxit.mobile.pdf.lite", "com.google.android.apps.classroom",
    "org.khanacademy.android", "org.coursera.android", "com.udemy.android", "org.wikipedia", "com.goodreads",
    "com.quizlet.quizletandroid", "com.byjus.thelearningapp")
add("news",
    "com.google.android.apps.magazines", "flipboard.app", "com.nytimes.android", "bbc.mobile.news.ww", "com.guardian",
    "com.cnn.mobile.android.phone", "com.washingtonpost.android", "com.thomsonreuters.reuters",
    "com.bloomberg.android.plus", "com.ft.news", "com.microsoft.amp.apps.bingnews", "com.ndtv.core",
    "com.toi.reader.activities", "com.eterno", "com.nis.app", "com.devhd.feedly", "com.innologica.inoreader",
    "net.frju.flym", "org.fox.ttrss", "com.nononsenseapps.feeder")

# ------------------------------------------------------------------ system utilities
add("settings",
    "com.android.settings", "com.xiaomi.misettings", "com.google.android.settings.intelligence")
add("clock",
    "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
    "com.oneplus.deskclock", "com.coloros.alarmclock", "com.android.BBKClock", "com.huawei.deskclock",
    "com.asus.deskclock", "com.simplemobiletools.clock", "org.fossify.clock",
    "com.samsung.android.app.watchmanager", "com.lge.clock", "com.sonyericsson.organizer",
    "com.htc.android.worldclock", "com.motorola.blur.alarmclock")
add("alarm", "com.alarmclock.xtreme.free", "com.alarmclock.xtreme", "com.amdroidalarmclock.amdroid")
add("sleep", "com.urbandroid.sleep", "com.northcube.sleepcycle", "com.ouraring.oura")
add("calculator",
    "com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator",
    "com.miui.calculator", "com.oneplus.calculator", "com.coloros.calculator", "com.android.bbkcalculator",
    "com.huawei.calculator", "com.asus.calculator", "com.simplemobiletools.calculator", "org.fossify.calculator",
    "com.microblink.photomath", "com.wolfram.android.alpha", "org.geogebra.android", "com.lge.calculator")
add("calendar",
    "com.google.android.calendar", "com.android.calendar", "com.samsung.android.calendar", "com.bbk.calendar",
    "com.coloros.calendar", "com.simplemobiletools.calendar.pro", "org.fossify.calendar",
    "ws.xsoh.etar", "com.appgenix.bizcal", "me.proton.android.calendar", "com.huawei.calendar",
    "com.asus.calendar")
add("tasks",
    "com.google.android.apps.tasks", "com.samsung.android.app.reminder", "com.ticktick.task", "com.todoist",
    "com.anydo", "com.microsoft.todos", "org.tasks", "com.habitnow")
add("notes",
    "com.google.android.keep", "com.samsung.android.app.notes", "com.samsung.android.app.memo", "com.miui.notes",
    "com.oneplus.note", "com.coloros.note", "com.oplus.note", "com.android.notes",
    "com.simplemobiletools.notes.pro", "org.fossify.notes", "com.evernote", "md.obsidian",
    "com.microsoft.office.onenote", "net.cozic.joplin", "com.automattic.simplenote", "com.standardnotes",
    "it.feio.android.omninotes", "it.feio.android.omninotes.foss", "com.notion.id",
    "com.socialnmobile.dictapps.notepad.color.note", "com.huawei.notepad",
    "com.google.android.apps.docs.editors.docs", "com.google.android.apps.docs.editors.sheets",
    "com.google.android.apps.docs.editors.slides", "com.microsoft.office.word", "com.microsoft.office.excel",
    "com.microsoft.office.powerpoint", "cn.wps.moffice_eng")
add("files",
    "com.google.android.apps.nbu.files", "com.google.android.documentsui", "com.android.documentsui",
    "com.mi.android.globalFileexplorer", "com.android.fileexplorer", "com.sec.android.app.myfiles",
    "com.oneplus.filemanager", "com.coloros.filemanager", "com.android.filemanager",
    "com.asus.filemanager", "com.huawei.hidisk", "com.ghisler.android.TotalCommander", "com.amaze.filemanager",
    "pl.solidexplorer2", "com.alphainventor.filemanager", "nextapp.fx", "com.mixplorer", "me.zhanghai.android.files",
    "com.estrongs.android.pop", "com.rhmsoft.fm", "com.simplemobiletools.filemanager.pro", "org.fossify.filemanager",
    "com.lonelycatgames.Xplore", "com.speedsoftware.rootexplorer")
add("downloads",
    "com.android.providers.downloads.ui", "com.dv.adm", "com.dv.adm.pay", "idm.internet.download.manager",
    "com.utorrent.client", "com.delphicoder.flud", "org.proninyaroslav.libretorrent", "com.frostwire.android")
add("cloud_storage",
    "com.google.android.apps.docs", "com.google.android.apps.subscriptions.red", "com.microsoft.skydrive",
    "com.dropbox.android", "com.box.android", "com.mega.android", "com.nextcloud.client", "com.pcloud.pcloud",
    "me.proton.android.drive", "com.owncloud.android", "com.baidu.netdisk", "com.tencent.weiyun",
    "com.huawei.android.hicloud", "com.samsung.android.scloud", "com.miui.cloudservice", "com.heytap.cloud",
    "com.synology.dsfile")
add("backup",
    "com.sec.android.easyMover", "com.miui.huanji", "com.oneplus.opbackup", "com.coloros.backuprestore",
    "com.huawei.KoBackup", "com.keramidas.TitaniumBackup", "com.miui.backup", "at.bitfire.davdroid")
add("send",
    "com.samsung.android.app.sharelive", "com.miui.mishare.connectivity", "com.xiaomi.midrop", "com.vivo.easyshare",
    "com.lenovo.anyshare.gps", "com.dewmobile.kuaiya.play")
add("security",
    "com.samsung.android.samsungpass", "com.miui.securitycenter", "com.miui.antispam", "com.coloros.safecenter",
    "com.iqoo.secure", "com.google.android.apps.safetyhub", "com.google.android.apps.kids.familylink",
    "com.google.android.apps.authenticator2", "com.azure.authenticator", "com.authy.authy", "org.fedorahosted.freeotp",
    "com.twofasapp", "io.ente.auth", "com.duosecurity.duomobile", "com.okta.android.auth", "com.yubico.yubioath",
    "com.beemdevelopment.aegis", "com.avast.android.mobilesecurity", "com.kms.free", "com.bitdefender.security",
    "com.eset.ems2.gp", "com.wsandroid.suite", "com.symantec.mobilesecurity", "com.malwarebytes.antimalware",
    "com.lookout", "com.antivirus")
add("key",
    "com.x8bit.bitwarden", "com.lastpass.lpandroid", "com.onepassword.android", "com.dashlane",
    "com.kunzisoft.keepass.free", "keepass2android.keepass2android", "proton.android.pass",
    "com.agilebits.onepassword", "com.callpod.android_apps.keeper")
add("lock", "com.domobile.applock", "com.sp.protector.free", "com.kii.safe", "com.samsung.knox.securefolder")
add("vpn",
    "com.nordvpn.android", "com.expressvpn.vpn", "ch.protonvpn.android", "com.surfshark.vpnclient.android",
    "com.privateinternetaccess.android", "com.cloudflare.onedotonedotonedotone", "free.vpn.unblock.proxy.turbovpn",
    "com.windscribe.vpn", "com.tunnelbear.android", "net.mullvad.mullvadvpn", "com.ixolit.ipvanish",
    "de.mobileconcepts.cyberghost", "hotspotshield.android.vpn", "com.psiphon3", "com.psiphon3.subscription",
    "org.torproject.android", "com.wireguard.android", "de.blinkt.openvpn", "net.openvpn.openvpn",
    "com.fortinet.forticlient_vpn", "com.cisco.anyconnect.vpn.android.avf", "com.paloaltonetworks.globalprotect",
    "app.intra", "org.outline.android.client", "com.v2ray.ang", "com.github.shadowsocks", "com.avast.android.vpn",
    "com.android.vpndialogs")
add("wifi",
    "com.tplink.tether", "com.asus.router", "com.ubnt.usurvey", "com.netgear.netgearup", "com.ookla.speedtest",
    "org.zwanoo.android.speedtest", "com.farproc.wifi.analyzer", "com.vrem.wifianalyzer", "com.overlook.android.fing",
    "com.instabridge.android", "com.xiaomi.router")
add("bluetooth", "com.android.bluetooth", "com.samsung.android.bluetooth")
add("battery",
    "com.google.android.apps.turbo", "com.samsung.android.lool", "com.miui.powerkeeper", "com.digibites.accubattery",
    "com.gsamlabs.bbm", "com.huawei.powergenie", "com.coloros.oppoguardelf", "com.sec.android.sdhms")
add("phone_manager",
    "com.coloros.phonemanager", "com.oppo.safe", "com.huawei.systemmanager", "com.hihonor.systemmanager",
    "com.asus.mobilemanager", "com.transsion.phonemaster", "com.oplus.phonemanager")
add("keyboard",
    "com.google.android.inputmethod.latin", "com.touchtype.swiftkey", "com.touchtype.swiftkey.beta",
    "com.samsung.android.honeyboard", "com.sec.android.inputmethod", "com.android.inputmethod.latin",
    "com.baidu.input", "com.baidu.input_mi", "com.sohu.inputmethod.sogou", "com.iflytek.inputmethod",
    "com.google.android.apps.inputmethod.hindi", "com.google.android.inputmethod.pinyin",
    "com.google.android.inputmethod.japanese", "com.google.android.inputmethod.korean",
    "com.google.android.apps.inputmethod.zhuyin", "com.google.android.apps.inputmethod.cantonese",
    "com.emoji.keyboard.touchpal", "com.jb.emoji.gokeyboard", "com.cootek.smartinputv5",
    "com.syntellia.fleksy.keyboard", "org.pocketworkstation.pckeyboard", "com.menny.android.anysoftkeyboard",
    "rkr.simplekeyboard.inputmethod", "helium314.keyboard", "org.dslul.openboard.inputmethod.latin",
    "com.simplemobiletools.keyboard", "org.fossify.keyboard", "com.android.inputmethod.pinyin")
add("print",
    "com.android.printspooler", "com.android.bips", "com.hp.android.printservice", "com.hp.printercontrol",
    "com.epson.iprint", "jp.co.canon.bsd.ad.pixmaprint", "com.brother.mfc.brprint", "com.google.android.apps.cloudprint",
    "com.dynamixsoftware.printershare")
add("scanner",
    "com.xiaomi.scanner", "com.coloros.ocrscanner", "com.google.android.apps.photos.scanner", "com.adobe.scan.android",
    "com.microsoft.office.officelens", "com.intsig.camscanner", "com.google.zxing.client.android", "me.scan.android.client",
    "com.gamma.scan", "com.thegrizzlylabs.geniusscan.free")
add("flashlight", "com.simplemobiletools.flashlight", "com.surpax.ledflashlight.panel")
add("compass", "com.miui.compass", "com.coloros.compass2")
add("translate",
    "com.google.android.apps.translate", "com.deepl.mobiletranslator", "com.microsoft.translator",
    "com.baidu.baidutranslate", "com.naver.labs.translator", "at.nk.tools.iTranslate", "com.yandex.translate",
    "com.samsung.android.app.interpreter", "com.xiaomi.aiasst.vision")
add("ai",
    "com.google.android.apps.bard", "com.openai.chatgpt", "com.anthropic.claude", "com.microsoft.copilot",
    "ai.perplexity.app.android", "com.deepseek.chat", "ai.x.grok", "com.nothing.ntessentialspace")
add("assistant",
    "com.google.android.apps.googleassistant", "com.amazon.dee.app", "com.samsung.android.bixby.agent",
    "com.microsoft.cortana", "com.miui.voiceassist", "com.heytap.speechassist", "com.vivo.agent",
    "com.huawei.vassistant", "com.samsung.android.bixby.service")
add("search",
    "com.google.android.googlequicksearchbox", "com.google.android.apps.searchlite", "com.microsoft.bing",
    "com.samsung.android.app.galaxyfinder", "com.android.quicksearchbox")
add("browser",
    "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary", "org.chromium.chrome",
    "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix", "org.mozilla.focus", "org.mozilla.klar",
    "org.mozilla.rocket", "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
    "com.opera.browser", "com.opera.browser.beta", "com.opera.mini.native", "com.opera.gx", "com.microsoft.emmx",
    "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta", "com.duckduckgo.mobile.android",
    "com.vivaldi.browser", "org.torproject.torbrowser", "com.kiwibrowser.browser", "com.UCMobile.intl",
    "com.uc.browser.en", "mobi.mgeek.TunnyBrowser", "com.ecosia.android", "org.bromite.bromite",
    "com.yandex.browser", "com.cloudmosa.puffinFree", "acr.browser.lightning", "com.stoutner.privacybrowser.standard",
    "org.adblockplus.browser", "com.mi.globalbrowser", "com.android.browser", "com.huawei.browser",
    "com.qwant.liberty", "com.tencent.mtt", "com.quark.browser", "com.heytap.browser", "com.coloros.browser",
    "com.vivo.browser", "com.oppo.browser")
add("app_store",
    "com.android.vending", "org.fdroid.fdroid", "com.aurora.store", "dev.imranr.obtainium", "com.looker.droidify",
    "com.machiav3lli.fdroid", "com.amazon.venezia", "com.sec.android.app.samsungapps", "com.xiaomi.mipicks",
    "com.xiaomi.market", "com.heytap.market", "com.oppo.market", "com.vivo.appstore", "com.huawei.appmarket",
    "com.hihonor.appmarket", "com.tencent.android.qqdownloader", "com.qihoo.appstore", "com.baidu.appsearch",
    "com.wandoujia.phoenix2", "com.lenovo.leos.appstore", "cm.aptoide.pt", "com.apkpure.aegon", "com.uptodown",
    "com.transsion.palmstore")

# ------------------------------------------------------------------ maps / travel / transit
add("maps",
    "com.google.android.apps.maps", "com.google.android.apps.mapslite", "com.google.earth", "com.here.app.maps",
    "net.osmand", "net.osmand.plus", "net.osmand.dev", "app.organicmaps", "com.mapswithme.maps.pro",
    "ru.yandex.yandexmaps", "com.baidu.BaiduMap", "com.autonavi.minimap", "com.tencent.map", "com.nhn.android.nmap",
    "net.daum.android.map", "com.google.android.apps.adm", "com.tripadvisor.tripadvisor", "com.yelp.android")
add("navigation",
    "com.waze", "com.sygic.aura", "com.tomtom.gplay.navapp", "ru.yandex.yandexnavi", "com.google.android.apps.navlite",
    "com.mapfactor.navigator")
add("train",
    "com.citymapper.app.release", "com.tranzmate", "ch.sbb.mobile.android.b2c", "de.hafas.android.db",
    "com.thetrainline", "com.vsct.vsc.mobile.horaireetresa.android", "at.oebb.ts", "cris.org.in.prs.ima",
    "in.redbus.android", "com.ixigo.train.ixitrain", "com.confirmtkt.lite", "com.railyatri.in.mobile", "de.flixbus.app")
add("flight",
    "com.makemytrip", "com.goibibo", "com.cleartrip.android", "net.skyscanner.android.main", "com.expedia.bookings",
    "com.kayak.android", "com.lufthansa.android.lufthansa", "com.ryanair.cheapflights", "com.united.mobile.android",
    "com.delta.mobile.android", "com.aa.android", "com.emirates.ek.android", "com.flightradar24free",
    "com.flightaware.android.liveFlightTracker", "com.google.android.apps.travel.onthego", "com.yatra.base",
    "com.easyjet.android", "com.wizzair.WizzAirApp")
add("home",
    "com.airbnb.android", "com.booking", "com.zillow.android.zillowmap", "com.redfin.android", "com.trulia.android",
    "com.timesgroup.magicbricks", "com.locon.housing", "com.rightmove.android", "de.immobilienscout24.android",
    "com.hcom.android")
add("car",
    "com.google.android.projection.gearhead", "com.teslamotors.tesla", "org.prowl.torque", "com.kajda.fuelio",
    "com.stationdm.bluelink", "de.bmw.connected.mobile20.row", "com.toyota.oneapp", "de.volkswagen.carnet.eu.eremote",
    "com.ford.fordpass")
add("ride",
    "com.ubercab", "com.ubercab.driver", "com.olacabs.customer", "com.lyft.android", "ee.mtakso.client",
    "com.grabtaxi.passenger", "com.careem.acma", "com.gojek.app", "com.didiglobal.passenger", "taxi.android.client",
    "com.mytaxi.passenger", "com.rapido.passenger", "sinet.startup.inDriver", "ru.yandex.taxi", "com.cabify.rider",
    "com.comuto", "com.limebike", "co.bird.android", "com.tier.app", "com.zoomcar")

# ------------------------------------------------------------------ food / shopping / tickets
add("food",
    "com.application.zomato", "in.swiggy.android", "com.ubercab.eats", "com.dd.doordash", "com.grubhub.android",
    "com.takeaway.android", "com.justeat.app.uk", "com.deliveroo.orderapp", "com.global.foodpanda.android",
    "com.mcdonalds.mobileapp", "com.opentable", "com.wolt.android", "com.talabat")
add("shop",
    "com.dunzo.user", "com.grofers.customerapp", "com.zeptoconsumerapp", "com.bigbasket.mobileapp",
    "in.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping", "com.flipkart.android", "com.ebay.mobile",
    "com.alibaba.aliexpresshd", "com.alibaba.intl.android.apps.poseidon", "com.shopee.ph", "com.shopee.my",
    "com.shopee.sg", "com.shopee.th", "com.shopee.vn", "com.shopee.id", "com.shopee.tw", "com.shopee.br",
    "com.lazada.android", "com.myntra.android", "com.meesho.supply", "com.tul.tatacliq", "com.fsn.nykaa",
    "com.ril.ajio", "com.jpl.jiomart", "com.etsy.android", "com.walmart.android", "com.target.ui",
    "com.costco.app.android", "com.ikea.kompis", "com.lidl.eci.lidlplus", "com.inditex.zara", "com.hm.goe",
    "com.einnovation.temu", "com.zzkko", "com.contextlogic.wish", "com.mercadolibre", "com.olx.southasia",
    "fr.vinted", "com.snapdeal.main", "com.jingdong.app.mall", "com.taobao.taobao", "com.tmall.wireless")
add("store",
    "com.squareup", "com.paypal.here", "com.shopify.mobile", "com.amazon.sellermobile.android", "com.etsy.android.soe")
add("ticket",
    "com.bt.bms", "com.ticketmaster.mobile.android.na", "com.stubhub", "com.eventbrite.attendee",
    "com.vividseats.android", "com.seatgeek.android", "com.fandango")
add("sports",
    "com.espn.score_center", "com.nbaimd.gametime.nba2011", "com.fifa.fifaapp.android", "com.cricbuzz.android",
    "com.july.cricinfo", "de.motain.iliga", "com.mobilefootie.wc2010", "com.sofascore.results",
    "com.bamnetworks.mobile.android.gameday.atbat", "com.nhl.gc1112.free")

# ------------------------------------------------------------------ money
add("bank",
    "com.csam.icici.bank.imobile", "com.snapwork.hdfc", "com.sbi.SBIFreedomPlus", "com.sbi.lotusintouch",
    "com.axis.mobile", "com.msf.kbank.mobile", "com.canarabank.mobility", "com.idfcfirstbank.optimus",
    "com.chase.sig.android", "com.infonow.bofa", "com.wf.wellsfargomobile", "com.konylabs.capitalone",
    "com.usaa.mobile.android.usaa", "com.citi.citimobile", "com.usbank.mobilebanking", "com.ally.MobileBanking",
    "com.discoverfinancial.mobile", "com.revolut.revolut", "co.uk.getmondo", "com.starlingbank.android",
    "de.number26.android", "uk.co.hsbc.hsbcukmobilebanking", "com.barclays.android.barclaysmobilebanking",
    "uk.co.santander.santanderUK", "com.ing.mobile", "com.db.pwcc.dbmobile", "com.bbva.bbvacontigo",
    "es.lacaixa.mobile.android.newwapicon", "com.santander.app", "ru.sberbankmobile", "com.idamob.tinkoff.android",
    "com.nu.production", "br.com.intermedium", "com.itau", "br.com.bb.android", "com.bradesco",
    "com.robinhood.android", "com.americanexpress.android.acctsvcs.us")
add("payments",
    "com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user", "in.org.npci.upiapp",
    "com.dreamplug.androidapp", "com.freecharge.android", "com.mobikwik_new", "com.sbi.upi", "com.bharatpe.app",
    "com.paypal.android.p2pmobile", "com.venmo", "com.squareup.cash", "com.zellepay.zelle",
    "com.transferwise.android", "com.myklarnamobile", "com.globe.gcash.android", "com.paymaya",
    "com.eg.android.AlipayGphone", "com.mercadopago.wallet", "com.picpay", "com.coinbase.android", "com.binance.dev",
    "com.kraken.invest.app", "com.Splitwise.SplitwiseMobile", "com.xiaomi.payment", "com.mipay.wallet.in",
    "com.paypal.merchant.client")
add("wallet",
    "com.google.android.apps.walletnfcrel", "com.samsung.android.spay", "com.samsung.android.spaymini",
    "com.miui.tsmclient", "com.wallet.crypto.trustapp", "io.metamask", "piuk.blockchain.android",
    "exodusmovement.exodus", "app.phantom", "com.ledger.live", "de.stocard.stocard", "me.hackerchick.catima",
    "protect.card_locker", "com.huawei.wallet")

# ------------------------------------------------------------------ health / wellness / home
add("health",
    "com.sec.android.app.shealth", "com.google.android.apps.healthdata", "com.mi.health", "com.huawei.health",
    "com.heytap.health", "com.coloros.healthservice", "org.iggymedia.periodtracker", "com.clue.android")
add("fitness",
    "com.google.android.apps.fitness", "com.fitbit.FitbitMobile", "com.garmin.android.apps.connectmobile", "com.strava",
    "com.nike.ntc", "com.nike.plusgps", "com.runtastic.android", "com.fitnesskeeper.runkeeper.pro",
    "com.mapmyrun.android2", "cc.pacer.androidapp", "com.xiaomi.wearable", "com.huami.watch.hmwatchmanager",
    "com.polar.polarflow", "com.stt.android.suunto", "com.whoop.android", "com.onepeloton.callisto",
    "com.freeletics.lite", "com.yazio.android", "com.sillens.shapeupclub", "com.myfitnesspal.android")
add("weight", "com.fitbod.fitbod", "je.fit")
add("meditate",
    "com.google.android.apps.wellbeing", "com.samsung.android.forest", "com.calm.android",
    "com.getsomeheadspace.android", "com.spotlightsix.zentimerlite2")
add("weather",
    "com.google.android.apps.weather", "com.sec.android.daemonapp", "com.miui.weather2", "com.coloros.weather2",
    "net.oneplus.weather", "com.vivo.weather", "com.huawei.android.totemweather", "com.accuweather.android",
    "com.weather.Weather", "com.yahoo.mobile.client.android.weather", "com.windyty.android",
    "com.apalon.weatherlive.free", "com.apalon.weatherradar.free", "com.samruston.weather", "org.breezyweather",
    "de.dwd.warnapp", "ru.gismeteo.gismeteo", "com.wunderground.android.weather", "com.nothing.weather",
    "com.asus.weather", "com.lge.weather", "com.htc.Weather")
add("smart_home",
    "com.google.android.apps.chromecast.app", "com.xiaomi.smarthome", "com.philips.lighting.hue2",
    "com.tplink.kasa_android", "com.tuya.smart", "com.samsung.android.oneconnect", "com.ringapp", "com.arlo.app",
    "com.nest.android", "io.homeassistant.companion.android", "com.eufylife.smarthome", "com.yeelight.cherry",
    "com.lifx.lifx", "com.hualai", "com.dyson.mobile.android", "com.irobot.home", "com.ecovacs.ecosphere",
    "org.openhab.habdroid", "com.lgeha.nuts")

# ------------------------------------------------------------------ validation
glyphs, ns = preview.load_all()
ids = {g[0] for g in glyphs}
bad = sorted({v for v in M.values() if v not in ids})
if bad:
    print("UNKNOWN GLYPH IDS:", bad)
    sys.exit(1)
if dups:
    print("CONFLICTS:", dups)
print("entries:", len(M))
used = sorted({v for v in M.values()})
print("glyphs used:", len(used), "unused:", sorted(ids - set(used)))
for k in M:
    assert all(c.isalnum() or c in "._" for c in k), k
    assert "." in k, k
body = ",\n".join(f'  "{k}": "{v}"' for k, v in M.items())
open(OUT, "w", encoding="utf-8", newline="\n").write("{\n" + body + "\n}\n")
json.load(open(OUT, encoding="utf-8"))
print("wrote", OUT)
