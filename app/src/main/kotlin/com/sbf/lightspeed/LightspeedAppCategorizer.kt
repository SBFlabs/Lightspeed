// Points-based categorizer. Order: exact package, package prefix, then Android category and
// keyword points on package AND label. Ties favour specific categories; utilities is last.
package com.sbf.lightspeed

import android.content.pm.ApplicationInfo
import android.os.Build

object LightspeedAppCategorizer {

    private val EXACT: Map<String, String> = mapOf(
        // comm
        "com.whatsapp" to "comm", "org.telegram.plus" to "comm", "com.discord" to "comm",
        "eu.faircode.email" to "comm", "com.microsoft.office.outlook" to "comm",
        "com.clintmoyer.callscope" to "comm",
        "com.sh.smart.caller" to "comm", "com.emeint.android.myservices" to "comm",
        "com.ucare.we" to "comm", "com.orange.mobinilandme" to "comm",
        // internet
        "com.brave.browser" to "internet", "eu.weblibre.gecko" to "internet",
        "org.quantumbadger.redreader" to "comm", "com.guardian" to "internet",
        "com.nytimes.android" to "internet", "io.raindrop.raindropio" to "internet",
        "org.breezyweather" to "internet", "one.jwr.interstellar" to "comm",
        "fe.linksheet.nightly" to "internet", "com.openai.chatgpt" to "internet",
        "com.google.android.apps.bard" to "internet", "com.anthropic.claude" to "internet",
        "com.foss.aihub" to "internet", "whl.trending.ai" to "internet",
        "lastchat.rikkafork.cocolal" to "internet", "com.tailscale.ipn" to "settings",
        "com.zaneschepke.wireguardautotunnel" to "security",
        "pan.alexander.tordnscrypt.stable" to "security",
        "com.prajwalch.torrentsearch" to "internet",
        "com.google.android.apps.docs" to "internet",
        "com.google.android.apps.subscriptions.red" to "internet",
        "io.filen.app" to "internet",
        // audio
        "audio.omgsoundboard" to "audio", "io.gitlab.libre_librivox_listener" to "audio",
        "app.podiumpodcasts.podium" to "audio", "com.huawei.smartaudio" to "audio",
        "com.adeeteya.classipod" to "audio",
        // graphics (photos, video, wallpapers)
        "org.videolan.vlc" to "graphics", "is.xyz.mpv" to "graphics",
        "org.jellyfin.mobile" to "graphics", "com.nuvio.app" to "graphics",
        "com.amazon.avod.thirdpartyclient" to "graphics", "org.polymorphicshade.tubular" to "graphics",
        "app.morphe.android.youtube" to "graphics", "com.deniscerri.ytdl" to "graphics",
        "io.ente.photos" to "graphics", "app.morphe.android.apps.photos" to "graphics",
        "com.example.memgallery" to "graphics", "la.daube.photochiotte" to "graphics",
        "ru.tech.imageresizershrinker" to "graphics", "com.lumina.wallpapers" to "settings",
        "com.github.cvzi.wallpaperexport" to "settings", "com.google.ar.lens" to "graphics",
        "com.ansah.shots_studio" to "graphics", "com.moiseum.dailyart2" to "graphics",
        "com.bluefirestudios.memeboard" to "audio",
        // games
        "com.epicgames.portal" to "games", "com.supercell.brawlstars" to "games",
        "com.headupgames.bridgeconstructorstudio" to "games", "com.thepixelhunt.thewreck" to "games",
        "io.github.vipaol.mobapp.game" to "games", "com.gambrinous.ultimatedungeoneering" to "games",
        "com.astroloop.game" to "games", "app.gamenative" to "games", "com.sidhant.puzzle" to "games",
        "com.playdigious.tmnt.epic" to "games", "com.fedeveloper95.games" to "games",
        // utilities
        "com.x8bit.bitwarden" to "security", "com.bitwarden.authenticator" to "security",
        "io.github.jd1378.otphelper" to "utilities", "dev.bikram.obtainx" to "stores",
        "com.looker.droidify" to "stores", "me.timschneeberger.shizustore" to "stores",
        "moe.shizuku.privileged.api" to "utilities", "com.hamondev.shevery" to "utilities",
        "io.github.samolego.canta" to "settings", "io.github.muntashirakon.appmanager" to "settings",
        "com.google.android.calendar" to "productivity", "com.google.android.apps.tasks" to "productivity",
        "com.google.android.safetycore" to "utilities", "com.google.android.apps.work.clouddpc" to "utilities",
        "com.google.android.apps.adm" to "utilities", "com.google.android.contactkeys" to "utilities",
        "com.google.android.apps.chromecast.app" to "settings",
        "org.kde.kdeconnect_tp" to "settings", "at.bitfire.davdroid" to "utilities",
        "md.obsidian" to "productivity", "com.dayoneapp.dayone" to "productivity",
        "com.habitrpg.android.habitica" to "productivity", "com.gideontek.phonetrack" to "utilities",
        "com.atharok.screentime" to "productivity", "com.dev.timeflow" to "productivity",
        "com.tk.quicksearch" to "productivity", "rk.android.app.shortcutmaker" to "settings",
        "tech.lolli.toolbox" to "settings", "com.mindful.code.studio.toxly.scanner" to "health",
        "com.networkscanner.app" to "settings", "com.kasumic.vpndetector" to "security",
        "com.funnycat.virustotal" to "security", "com.franko.privacyguard" to "security",
        "org.privacyguides.verifiedapps" to "security",
        "com.broadlink.ble" to "settings", "cn.com.broadlink.econtrol.international" to "settings",
        "org.futo.inputmethod.latin" to "productivity",
        // finance
        "enbd.mobilebanking.egyptmobile" to "finance", "enbd.mobilebanking.egyptretailtoken" to "finance",
        "com.egyptianbanks.instapay" to "finance", "gtpay.gtronicspay.com" to "finance",
        "com.halanuser" to "finance",
        // learning and books
        "com.duolingo" to "learning", "com.ichi2.anki" to "learning",
        "org.koreader.launcher.fdroid" to "learning", "com.anxcye.anx_reader" to "learning",
        "universe.constellation.orion.viewer" to "learning", "com.google.android.apps.books" to "learning",
        "com.languageapp.gendie" to "learning", "com.floflacards.app" to "learning",
        "com.odnovolov.forgetmenot" to "learning",
        "com.gorden.dayexam" to "learning", "com.storyshots.android" to "learning",
        "com.troikoss.pensum" to "settings", "com.wordtracer.app" to "games",
        "org.goldenquran.freesoft" to "learning", "org.erosson.freecbt" to "learning",
        "ca.reev.lifeintheuk" to "learning", "com.drugscom.app" to "learning",
        "com.bbi.accorg.cardiosource.american_college_of_cardiology" to "learning",
        "org.diabetes.american_diabetes_association_standards_of_medical_care" to "learning",
        // Additional utilities, security, and productivity apps
        "com.transsion.phonemaster" to "settings",
        "com.transsion.theme" to "settings",
        "com.transsion.tips" to "learning",
        "s1m.savertuner" to "utilities",
        "eu.toneiv.ubktouch" to "utilities",
        "io.github.kennethchoinfosec.haven" to "security",
        "com.kaleedtc.privacium" to "security",
        "com.techtrest.privamatic" to "security",
        "xyz.ptgms.tosdr" to "security",
        "com.orion.store" to "stores",
        "com.trevornk.ramblr" to "productivity",
        "com.musheer360.swiftslate" to "productivity",
        "com.adilhanney.no_more_background" to "settings",
        "com.jksalcedo.librefind" to "settings",
        "com.fadseclab.fadocx" to "productivity",
        "com.example.lifedots" to "productivity",
        "app.inkos" to "productivity",
        "com.kieronquinn.app.darq" to "utilities",
        "com.iyps" to "security",
        "com.sbf.lightspeed" to "utilities",
        "com.sbf.lightspeed.nightly" to "utilities",
        "com.talabat" to "internet",
        // Extended regional & open-source catalog
        "me.x2gd4.dollphone" to "settings",
        "dev.duofold" to "graphics",
        "dev.renkinproject.renkin" to "settings",
        "com.goodecho.app" to "audio",
        "com.droid.dolphy" to "utilities",
        "day.vitayuzu.neodb" to "learning",
        "com.transsion.compass" to "utilities",
        "com.transsnet.store" to "stores",
        "com.dessalines.rankmyfavs" to "learning",
        "com.looker.kenko" to "health",
        "com.moh.sh.app.shade" to "security",
        "eu.darken.myperm" to "security",
        "io.github.anrinion.baseline" to "health",
        "com.saad_khan_rind.atmosphere_effect" to "settings",
        "si.jakobkreft.aftergleam" to "learning",
        "com.cardpop.app" to "learning",
        "com.maazm7d.termuxhub" to "settings",
        "io.github.swiftstagrime.termuxrunner" to "settings",
        "com.github.android" to "internet",
        "eu.darken.sdmse" to "settings",
        "com.evo.inware" to "settings",
        "com.android.vending" to "stores",
        "com.shelf.app" to "stores",
        "com.transsion.calendar" to "productivity",
        "com.transsion.notebook" to "learning",
        "com.google.android.googlequicksearchbox" to "internet",
        "com.amazon.mshop.android.shopping" to "internet",
        "in.blueplanetapps.oceansofai" to "internet",
        "com.android.stk" to "comm",
        "com.efgh.customer" to "finance",
        "com.transsion.healthlife" to "health",
        "com.coco.typeaction" to "productivity",
        "com.amanoteam.kurt" to "security",
        "com.app.aha" to "learning",
        "com.akbon.myd" to "settings",
        "com.google.android.apps.labs.language.tailwind" to "learning",
        "com.smartlife.nebula" to "settings",
        // Global well-known apps
        "com.whatsapp.w4b" to "comm",
        "org.telegram.messenger" to "comm",
        "org.thoughtcrime.securesms" to "comm",
        "com.facebook.orca" to "comm",
        "com.snapchat.android" to "comm",
        "com.viber.voip" to "comm",
        "com.truecaller" to "comm",
        "imo.android.imoim" to "comm",
        "us.zoom.videomeetings" to "comm",
        "com.microsoft.teams" to "comm",
        "com.google.android.apps.messaging" to "comm",
        "com.google.android.dialer" to "comm",
        "com.google.android.gm" to "comm",
        "com.android.chrome" to "internet",
        "org.mozilla.firefox" to "internet",
        "com.microsoft.emmx" to "internet",
        "com.opera.browser" to "internet",
        "com.sec.android.app.sbrowser" to "internet",
        "com.duckduckgo.mobile.android" to "internet",
        "com.spotify.music" to "audio",
        "com.google.android.apps.youtube.music" to "audio",
        "com.anghami" to "audio",
        "com.soundcloud.android" to "audio",
        "com.shazam.android" to "audio",
        "com.audible.application" to "audio",
        "com.jio.media.jiobeats" to "audio",
        "com.gaana" to "audio",
        "com.google.android.youtube" to "graphics",
        "com.netflix.mediaclient" to "graphics",
        "in.startv.hotstar" to "graphics",
        "net.mbc.shahid" to "graphics",
        "com.google.android.apps.photos" to "graphics",
        "com.zhiliaoapp.musically" to "graphics",
        "com.mxtech.videoplayer.ad" to "graphics",
        "com.phonepe.app" to "finance",
        "net.one97.paytm" to "finance",
        "com.google.android.apps.nbu.paisa.user" to "finance",
        "in.org.npci.upiapp" to "finance",
        "com.paypal.android.p2pmobile" to "finance",
        "com.venmo" to "finance",
        "com.squareup.cash" to "finance",
        "com.coinbase.android" to "finance",
        "com.robinhood.android" to "finance",
        "com.chase.sig.android" to "finance",
        "com.infonow.bofa" to "finance",
        "com.wf.wellsfargomobile" to "finance",
        "com.khanacademy.android" to "learning",
        "com.google.android.apps.classroom" to "learning",
        "org.coursera.android" to "learning",
        "com.google.android.apps.authenticator2" to "security",
        "com.azure.authenticator" to "security",
        "com.authy.authy" to "security",
        "com.lastpass.lpandroid" to "security",
        "com.beemdevelopment.aegis" to "security",
        "ch.protonvpn.android" to "security",
        "com.nordvpn.android" to "security",
        "com.expressvpn.vpn" to "security",
        "com.wireguard.android" to "security",
        "com.google.android.keep" to "productivity",
        "com.google.android.apps.docs.editors.docs" to "productivity",
        "com.google.android.apps.docs.editors.sheets" to "productivity",
        "com.google.android.apps.docs.editors.slides" to "productivity",
        "com.microsoft.office.word" to "productivity",
        "com.microsoft.office.excel" to "productivity",
        "com.microsoft.office.powerpoint" to "productivity",
        "com.microsoft.office.onenote" to "productivity",
        "com.adobe.reader" to "productivity",
        "com.todoist" to "productivity",
        "notion.id" to "productivity",
        "com.samsung.android.app.notes" to "productivity",
        "com.sec.android.app.samsungapps" to "stores",
        "com.xiaomi.mipicks" to "stores",
        "com.huawei.appmarket" to "stores",
        "com.aurora.store" to "stores",
        "org.fdroid.fdroid" to "stores",
        "com.amazon.venezia" to "stores",
        "com.transsion.palmstore" to "stores",
        "com.google.android.apps.fitness" to "health",
        "com.sec.android.app.shealth" to "health",
        "com.fitbit.fitbitmobile" to "health",
        "com.myfitnesspal.android" to "health",
        "com.strava" to "health",
        // Brazil & Latin America
        "com.mercadolibre" to "internet",
        "com.mercadopago.wallet" to "finance",
        "com.nu.production" to "finance",
        "com.picpay" to "finance",
        "br.com.bb.android" to "finance",
        "br.com.brainweb.ifood" to "internet",
        "com.grability.rappi" to "internet",
        "com.globo.globotv" to "graphics",
        "com.taxis99" to "internet",
        "com.despegar.decidir" to "internet",
        // Europe (Germany, France, Spain, Italy, Russia, Turkey)
        "de.hafas.android.db" to "internet",
        "de.number26.android" to "finance",
        "com.starfinanz.smob.android.sfinanzstatus" to "finance",
        "de.ingdiba.bankingapp" to "finance",
        "de.zdf.zdfmediathek" to "graphics",
        "de.ard.ardmediathek" to "graphics",
        "com.ebay.kleinanzeigen" to "internet",
        "fr.doctolib.www" to "health",
        "fr.leboncoin" to "internet",
        "com.boursorama.android.clients" to "finance",
        "com.lydia" to "finance",
        "deezer.android.app" to "audio",
        "tv.molotov.app" to "graphics",
        "com.bbva.bbvacontigo" to "finance",
        "com.santander.app" to "finance",
        "com.wallapop" to "internet",
        "posteitaliane.posteapp.apppostepay" to "finance",
        "it.subito" to "internet",
        "com.vkontakte.android" to "comm",
        "ru.ok.android" to "comm",
        "com.yandex.browser" to "internet",
        "ru.yandex.taxi" to "internet",
        "ru.yandex.yandexmaps" to "internet",
        "ru.yandex.music" to "audio",
        "ru.sberbankmobile" to "finance",
        "ru.tinkoff.activities" to "finance",
        "ru.ozon.app.android" to "internet",
        "ru.wildberries.buyer" to "internet",
        "com.avito.android" to "internet",
        "com.getir" to "internet",
        "com.pozitron.hepsiburada" to "internet",
        "com.garanti.cepsubesi" to "finance",
        "com.turkcell.bip" to "comm",
        // East Asia (China, Japan, Korea)
        "com.tencent.mm" to "comm",
        "com.tencent.mobileqq" to "comm",
        "com.sina.weibo" to "comm",
        "com.eg.android.AlipayGphone" to "finance",
        "com.taobao.taobao" to "internet",
        "com.jingdong.app.mall" to "internet",
        "tv.danmaku.bili" to "graphics",
        "com.baidu.searchbox" to "internet",
        "com.baidu.BaiduMap" to "internet",
        "com.netease.cloudmusic" to "audio",
        "com.qiyi.video" to "graphics",
        "com.xingin.xhs" to "internet",
        "com.zhihu.android" to "learning",
        "com.ss.android.ugc.aweme" to "graphics",
        "jp.naver.line.android" to "comm",
        "jp.co.yahoo.android.yjtop" to "internet",
        "jp.ne.paypay.android.app" to "finance",
        "jp.co.rakuten.android" to "internet",
        "com.kouzoh.mercari" to "internet",
        "tv.abema" to "graphics",
        "com.kakao.talk" to "comm",
        "com.kakaobank.channel" to "finance",
        "com.kakaopay.app" to "finance",
        "com.kakao.taxi" to "internet",
        "com.nhn.android.search" to "internet",
        "com.nhn.android.webtoon" to "graphics",
        "com.nhn.android.nmap" to "internet",
        "viva.republica.toss" to "finance",
        "com.coupang.mobile" to "internet",
        // Southeast Asia (Indonesia, Vietnam, Thailand, Philippines)
        "com.gojek.app" to "internet",
        "com.tokopedia.tkpd" to "internet",
        "id.dana" to "finance",
        "ovo.id" to "finance",
        "com.bca" to "finance",
        "com.traveloka.android" to "internet",
        "com.vidio.android" to "graphics",
        "com.zing.zalo" to "comm",
        "com.mservice.momotransfer" to "finance",
        "com.zing.mp3" to "audio",
        "th.co.truemoney.wallet" to "finance",
        "com.kasikorn.retail.mbanking.wap" to "finance",
        "com.globe.gcash.android" to "finance",
        "com.paymaya" to "finance",
        "com.grabtaxi.passenger" to "internet",
        "com.lazada.android" to "internet",
        // Africa (Nigeria, Egypt, South Africa)
        "team.opay.pay" to "finance",
        "com.transsnet.palmpay" to "finance",
        "com.kudabank.app" to "finance",
        "com.jumia.android" to "internet",
        "com.gtbank.gtworldv1" to "finance",
        "com.firstbank.firstmobile" to "finance",
        "com.fawry.myfawry" to "finance",
        "com.telda.app" to "finance",
        "com.thndr.app" to "finance",
        "fnb.android.client" to "finance",
        "com.takealot.android" to "internet",
        "com.dstv.now" to "graphics",
    )

    private val PREFIXES: List<Pair<String, String>> = listOf(
        "com.mixplorer" to "utilities",
        "com.termux" to "settings",
        "com.arlosoft.macrodroid" to "settings",
        "com.leanbitlab.leantype" to "productivity",
        "eu.darken" to "utilities",
        "com.donnnno.arcticons" to "settings",
        "com.google.android.apps.youtube" to "graphics",
        "com.ustwo" to "games",
        "com.playsidestudios" to "games",
        "com.popreach.egs" to "games",
        "net.amanita_design" to "games",
    )

    private val COMM_PHRASES: List<String> = listOf(
        "call recorder", "callrecorder", "call recording", "callrecording",
        "call blocker", "callblocker", "caller id", "callerid",
        "callvault", "call vault",
        "مسجل المكالمات", "تسجيل المكالمات", "تسجيل مكالمات", "حظر المكالمات", "معرف المتصل",
        "कॉल रिकॉर्डर", "कॉल रिकॉर्डिंग", "कॉलर आईडी",
        "grabador de llamadas", "grabadora de llamadas", "grabar llamadas", "bloqueador de llamadas", "identificador de llamadas",
    )

    private val GAMES = listOf("game", "games", "gaming", "arcade", "puzzle", "racer", "quest", "craft",
        "simulator", "dungeon", "epicgames", "egs", "rpg", "strategy", "trivia", "chess", "solitaire", "adventure")
    private val COMM = listOf("chat", "message", "messenger", "messaging", "mail", "email", "dialer", "sms",
        "contacts", "telegram", "whatsapp", "signal", "discord", "viber", "slack", "call", "calls",
        "social", "dating", "mastodon")
    private val INTERNET = listOf("browser", "browse", "gecko", "firefox", "chrome", "brave", "web", "news",
        "weather", "vpn", "dns", "wireguard", "tailscale", "torrent", "reddit", "forum", "bookmark", "rss",
        "feed", "maps", "navigation", "translate", "ai", "gpt", "gemini", "assistant")
    private val AUDIO = listOf("music", "audio", "sound", "soundboard", "player", "radio", "podcast",
        "podcasts", "audiobook", "listener", "recorder", "equalizer", "spotify", "song", "lyrics")
    private val GRAPHICS = listOf("camera", "photo", "photos", "gallery", "image", "images", "video", "videos",
        "paint", "draw", "wallpaper", "wallpapers", "icon", "icons", "sketch", "pictures", "screenshot",
        "stream", "streaming", "movie", "movies", "tv", "anime")
    private val UTILITIES = listOf("tool", "tools", "toolbox", "toolkit", "calc", "calculator", "clock",
        "timer", "alarm", "file", "explorer", "clean", "cleaner", "battery", "backup", "manager", "scanner",
        "scan", "pdf", "notes", "note", "notepad", "todo", "tasks", "calendar", "password", "authenticator",
        "otp", "keyboard", "launcher", "shortcut", "shortcuts", "terminal", "termux", "adb", "shizuku",
        "privacy", "security", "monitor", "widget", "widgets", "sync", "clipboard", "nfc", "qr", "barcode")

    private val SECURITY = listOf("security", "privacy", "vpn", "antivirus", "firewall", "password",
        "passwords", "authenticator", "vault", "lock", "applock", "2fa", "otp", "encrypt", "keepass",
        "bitwarden", "أمان", "الأمان", "حماية", "الحماية", "خصوصية", "الخصوصية", "قفل", "فيروسات",
        "مصادقة", "सुरक्षा", "गोपनीयता", "लॉक", "पासवर्ड", "वीपीएन")
    private val PRODUCTIVITY = listOf("notes", "note", "notepad", "todo", "tasks", "task", "calendar",
        "docs", "doc", "office", "word", "excel", "sheets", "slides", "pdf", "planner", "reminder",
        "reminders", "scanner", "keyboard", "clipboard", "notion", "ملاحظات", "مهام", "تقويم", "مذكرة",
        "تذكير", "مستندات", "नोट्स", "कैलेंडर", "रिमाइंडर", "दस्तावेज")
    private val HEALTH = listOf("health", "fitness", "workout", "steps", "pedometer", "yoga",
        "meditation", "sleep", "diet", "calorie", "calories", "medical", "pharmacy", "doctor", "pill",
        "wellness", "hydration", "صحة", "الصحة", "لياقة", "رياضة", "طبيب", "صيدلية", "دواء", "نوم",
        "خطوات", "स्वास्थ्य", "फिटनेस", "योग", "दवा", "डॉक्टर", "नींद")

    private val GAMES_L10N = listOf("لعبة", "اللعبة", "العاب", "ألعاب", "لعب",
        "गेम", "गेम्स", "खेल",
        "juego", "juegos", "jugar", "carreras", "rompecabezas")
    private val COMM_L10N = listOf("رسائل", "رسالة", "دردشة", "محادثة", "محادثات", "مراسلة", "بريد", "البريد",
        "مكالمات", "المكالمات",
        "संदेश", "चैट", "मैसेज", "मैसेंजर", "ईमेल", "डायलर",
        "mensajes", "mensajería", "mensajeria", "correo", "llamadas", "contactos")
    private val INTERNET_L10N = listOf("متصفح", "إنترنت", "انترنت", "الإنترنت", "الانترنت", "أخبار", "اخبار",
        "خرائط", "تسوق", "التسوق",
        "ब्राउज़र", "ब्राउजर", "इंटरनेट", "समाचार", "न्यूज़", "न्यूज", "नक्शा", "शॉपिंग",
        "navegador", "noticias", "mapas", "compras")
    private val AUDIO_L10N = listOf("موسيقى", "أغاني", "اغاني", "راديو", "إذاعة", "بودكاست",
        "صوت", "الصوت", "صوتي",
        "संगीत", "गाने", "गाना", "रेडियो", "पॉडकास्ट", "ऑडियो", "म्यूज़िक", "म्यूजिक",
        "música", "musica", "canciones", "grabadora")
    private val GRAPHICS_L10N = listOf("فيديو", "صور", "الصور", "معرض", "كاميرا", "تصوير",
        "वीडियो", "फोटो", "कैमरा", "गैलरी", "तस्वीर",
        "vídeo", "galería", "galeria", "cámara", "camara", "fotos", "imágenes", "imagenes")
    private val FINANCE_L10N = listOf("بنك", "البنك", "مصرف", "المصرف", "محفظة", "دفع", "الدفع", "مدفوعات",
        "بطاقة", "قرض", "القرض", "استثمار",
        "बैंक", "बैंकिंग", "भुगतान", "वॉलेट", "यूपीआई", "लोन", "निवेश",
        "banco", "billetera", "pagos", "préstamo", "prestamo", "tarjeta", "finanzas", "dinero")
    private val LEARNING_L10N = listOf("قرآن", "القرآن", "تعلم", "التعلم", "تعليم", "دروس", "قاموس",
        "كتب", "الكتب", "كتاب", "الكتاب", "لغات", "امتحان",
        "सीखें", "शिक्षा", "पढ़ाई", "किताब", "किताबें", "शब्दकोश", "कुरान", "परीक्षा", "भाषा",
        "aprender", "aprende", "diccionario", "libros", "libro", "lectura", "curso", "escuela",
        "universidad", "idiomas", "examen", "corán", "coran")
    private val UTILITIES_L10N = listOf("أدوات", "ادوات", "حاسبة", "ساعة", "الساعة", "منبه", "المنبه",
        "مؤقت", "المؤقت", "ملفات", "تنظيف", "منظف", "بطارية", "احتياطي", "كشاف", "بوصلة",
        "टूल", "टूल्स", "कैलकुलेटर", "घड़ी", "अलार्म", "फाइल", "फ़ाइल", "क्लीनर", "बैटरी", "बैकअप", "टॉर्च", "कंपास",
        "herramientas", "calculadora", "reloj", "alarma", "archivos", "limpiador", "batería", "bateria",
        "linterna", "brújula", "brujula", "temporizador", "cronómetro", "cronometro")

    private val GAMES_L10N_2 = listOf(
        "jogo", "jogos", "jogar", "tabuleiro",
        "jeux", "jouer",
        "spiel", "spiele", "spielen", "rätsel", "raetsel", "schach",
        "игра", "игры", "играть", "головоломка", "шахматы",
        "oyun", "oyunlar", "oyunu", "oyna", "bulmaca",
        "permainan", "mainkan")
    private val COMM_L10N_2 = listOf(
        "mensagem", "mensagens", "conversa", "conversas", "chamadas", "correio", "contatos",
        "messagerie", "courriel", "appels", "discuter", "contacts",
        "nachrichten", "nachricht", "anrufe", "kontakte", "plaudern", "postfach",
        "сообщения", "сообщение", "звонки", "звонок", "контакты", "переписка",
        "mesaj", "mesajlar", "sohbet", "aramalar", "kişiler", "kisiler", "rehber",
        "pesan", "obrolan", "panggilan", "kontak")
    private val INTERNET_L10N_2 = listOf(
        "navegação", "navegacao", "roteiro",
        "navigateur", "actualités", "actualites", "nouvelles",
        "webbrowser", "zeitung", "landkarte", "kartendienst",
        "браузер", "новости", "карты", "покупки", "погода",
        "tarayıcı", "tarayici", "haberler", "harita", "haritalar", "alışveriş", "alisveris",
        "peramban", "berita", "peta", "belanja")
    private val AUDIO_L10N_2 = listOf(
        "canções", "cancoes", "gravador",
        "musique", "chansons", "balado", "chanson",
        "musik", "lieder", "hörbuch", "hoerbuch", "klang",
        "музыка", "песни", "песня", "аудиокнига",
        "müzik", "muzik", "şarkılar", "sarkilar", "şarkı", "sarki", "sesler",
        "lagu", "suara")
    private val GRAPHICS_L10N_2 = listOf(
        "fotografia", "fotografias", "desenho", "desenhar", "filmadora",
        "galerie", "vidéos", "videos", "dessin", "peinture",
        "kamera", "bilder", "zeichnung", "gemälde", "hintergrund",
        "галерея", "камера", "фотографии", "видео", "рисунок", "обои",
        "galeri", "fotoğraflar", "fotograflar", "fotoğraf", "fotograf", "resimler",
        "gambar", "lukisan", "potret")
    private val FINANCE_L10N_2 = listOf(
        "carteira", "pagamentos", "pagamento", "empréstimo", "emprestimo", "investimento", "finanças", "financas",
        "banque", "portefeuille", "paiements", "paiement", "crédit", "credit", "argent",
        "sparkasse", "finanzen", "bezahlen", "überweisung", "ueberweisung", "kredit", "kredite", "geldbeutel",
        "банк", "банкинг", "кошелек", "кошелёк", "платежи", "оплата", "инвестиции", "деньги",
        "banka", "bankacılık", "bankacilik", "cüzdan", "cuzdan", "ödemeler", "odemeler", "yatırım", "yatirim",
        "dompet", "pembayaran", "keuangan", "pinjaman", "investasi")
    private val LEARNING_L10N_2 = listOf(
        "estudo", "estudar", "dicionário", "dicionario", "educação", "educacao", "lições", "licoes",
        "apprendre", "dictionnaire", "livres", "livre", "cours", "études", "etudes", "leçon", "lecon",
        "lernen", "wörterbuch", "woerterbuch", "bücher", "buecher", "unterricht", "studium", "lektion",
        "учеба", "учёба", "словарь", "книги", "книга", "уроки", "обучение", "экзамен",
        "öğren", "ogren", "öğrenme", "ogrenme", "sözlük", "sozluk", "kitap", "kitaplar", "dersler", "eğitim", "egitim",
        "belajar", "kamus", "buku", "pelajaran", "edukasi")
    private val UTILITIES_L10N_2 = listOf(
        "ferramentas", "despertador", "bússola", "bussola",
        "outils", "calculatrice", "horloge", "réveil", "reveil", "fichiers", "nettoyeur", "batterie", "boussole", "minuterie",
        "werkzeuge", "taschenrechner", "wecker", "dateien", "reiniger", "taschenlampe", "kompass", "stoppuhr",
        "инструменты", "калькулятор", "часы", "будильник", "файлы", "очистка", "батарея", "фонарик", "секундомер",
        "araçlar", "araclar", "hesaplama", "saat", "çalar", "dosyalar", "temizleyici", "fener", "pusula", "kronometre",
        "perkakas", "kalkulator", "berkas", "pembersih")

    private val FINANCE = listOf("bank", "banking", "pay", "wallet", "finance", "money", "card", "loan", "instapay", "atm")
    private val LEARNING = listOf("learn", "learning", "study", "dictionary", "quran", "anki", "flashcards",
        "course", "exam", "book", "books", "reader", "ebook", "language", "lesson", "quiz", "school", "university")

    private fun tokens(s: String): List<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.length >= 2 }

    // Each keyword counts once. Words of 4 letters or fewer must equal a whole token.
    private fun points(tokens: List<String>, words: List<String>): Int {
        var n = 0
        for (w in words) {
            val hit = tokens.any { if (w.length <= 4) it == w else it.contains(w) }
            if (hit) n += 1
        }
        return n
    }

    fun categorize(appInfo: ApplicationInfo, pkg: String, label: String): String {
        val pkgLower = pkg.lowercase()
        val labelLower = label.lowercase()

        if (pkgLower == "com.android.settings" || pkgLower.endsWith(".settings") || labelLower.endsWith("settings")) {
            return "settings"
        }

        EXACT[pkgLower]?.let { return it }
        for ((prefix, cat) in PREFIXES) {
            if (pkgLower.startsWith(prefix)) return cat
        }

        val labelSpaced = labelLower.replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ")
        for (phrase in COMM_PHRASES) {
            if (labelSpaced.contains(phrase) || pkgLower.contains(phrase.replace(" ", ""))) return "comm"
        }

        var gamesScore = 0
        var commScore = 0
        var internetScore = 0
        var audioScore = 0
        var graphicsScore = 0
        var financeScore = 0
        var learningScore = 0
        var utilitiesScore = 0
        var securityScore = 0
        var productivityScore = 0
        var healthScore = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> gamesScore += 3
                ApplicationInfo.CATEGORY_AUDIO -> audioScore += 3
                ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> graphicsScore += 3
                ApplicationInfo.CATEGORY_SOCIAL -> commScore += 3
                ApplicationInfo.CATEGORY_NEWS, ApplicationInfo.CATEGORY_MAPS -> internetScore += 3
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> productivityScore += 3
            }
        }

        @Suppress("DEPRECATION")
        if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
            gamesScore += 3
        }

        val pt = tokens(pkgLower)
        val lt = tokens(labelLower)
        gamesScore += points(pt, GAMES) + points(lt, GAMES)
        commScore += points(pt, COMM) + points(lt, COMM)
        internetScore += points(pt, INTERNET) + points(lt, INTERNET)
        audioScore += points(pt, AUDIO) + points(lt, AUDIO)
        graphicsScore += points(pt, GRAPHICS) + points(lt, GRAPHICS)
        financeScore += points(pt, FINANCE) + points(lt, FINANCE)
        learningScore += points(pt, LEARNING) + points(lt, LEARNING)
        utilitiesScore += points(pt, UTILITIES) + points(lt, UTILITIES)
        securityScore += points(pt, SECURITY) + points(lt, SECURITY)
        productivityScore += points(pt, PRODUCTIVITY) + points(lt, PRODUCTIVITY)
        healthScore += points(pt, HEALTH) + points(lt, HEALTH)
        gamesScore += points(pt, GAMES_L10N) + points(lt, GAMES_L10N)
        commScore += points(pt, COMM_L10N) + points(lt, COMM_L10N)
        internetScore += points(pt, INTERNET_L10N) + points(lt, INTERNET_L10N)
        audioScore += points(pt, AUDIO_L10N) + points(lt, AUDIO_L10N)
        graphicsScore += points(pt, GRAPHICS_L10N) + points(lt, GRAPHICS_L10N)
        financeScore += points(pt, FINANCE_L10N) + points(lt, FINANCE_L10N)
        learningScore += points(pt, LEARNING_L10N) + points(lt, LEARNING_L10N)
        utilitiesScore += points(pt, UTILITIES_L10N) + points(lt, UTILITIES_L10N)
        gamesScore += points(pt, GAMES_L10N_2) + points(lt, GAMES_L10N_2)
        commScore += points(pt, COMM_L10N_2) + points(lt, COMM_L10N_2)
        internetScore += points(pt, INTERNET_L10N_2) + points(lt, INTERNET_L10N_2)
        audioScore += points(pt, AUDIO_L10N_2) + points(lt, AUDIO_L10N_2)
        graphicsScore += points(pt, GRAPHICS_L10N_2) + points(lt, GRAPHICS_L10N_2)
        financeScore += points(pt, FINANCE_L10N_2) + points(lt, FINANCE_L10N_2)
        learningScore += points(pt, LEARNING_L10N_2) + points(lt, LEARNING_L10N_2)
        utilitiesScore += points(pt, UTILITIES_L10N_2) + points(lt, UTILITIES_L10N_2)

        val scores = listOf(
            "games" to gamesScore,
            "comm" to commScore,
            "internet" to internetScore,
            "audio" to audioScore,
            "graphics" to graphicsScore,
            "finance" to financeScore,
            "learning" to learningScore,
            "security" to securityScore,
            "productivity" to productivityScore,
            "health" to healthScore,
            "utilities" to utilitiesScore,
        )

        val maxScore = scores.maxOf { it.second }
        if (maxScore <= 0) {
            return "other"
        }

        return scores.first { it.second == maxScore }.first
    }
}
