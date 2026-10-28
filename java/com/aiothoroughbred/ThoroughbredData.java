package com.aiothoroughbred;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.horse.Horse;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ThoroughbredData {
    private static final String NBT_KEY = "AIOThoroughbred";
    private static volatile String activeClientLangCode = "en_us";

    public static void setActiveClientLangCode(String langCode) {
        if (langCode != null && !langCode.isBlank()) {
            activeClientLangCode = langCode.trim().toLowerCase(Locale.ROOT);
        }
    }

    public static String getActiveLangCode() {
        try {
            String fromLang = net.minecraft.locale.Language.getInstance().getOrDefault("aiothoroughbred.lang.code", "");
            if (fromLang != null && !fromLang.isBlank()) {
                return fromLang.trim().toLowerCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
        }
        return activeClientLangCode != null ? activeClientLangCode : "en_us";
    }

    public static boolean isTraditionalChinese() {
        String c = getActiveLangCode();
        return c.startsWith("zh_tw") || c.startsWith("zh_hk") || c.startsWith("zh_mo") || c.equals("lzh");
    }

    public static boolean isSimplifiedChinese() {
        String c = getActiveLangCode();
        return c.startsWith("zh_cn") || c.startsWith("zh_sg") || (c.startsWith("zh") && !isTraditionalChinese());
    }

    public static boolean isJapanese() {
        String c = getActiveLangCode();
        return c.startsWith("ja");
    }

    // =========================================================================
    // EXTERNAL USER-EDITABLE TRANSLATION SYSTEM (aiothoroughbred_translations/)
    // Allows players to edit any HUD, GUI, Stat Card, Tote Board, Strategy,
    // Trait, Ailment, or Injury phrase outside of items in JSON files on disk!
    // =========================================================================
    private static final java.util.Map<String, String[]> DEFAULT_TRANSLATIONS = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, java.util.Map<String, String>> USER_TRANSLATION_OVERRIDES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> TRANSLATION_FILE_MTIMES = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean translationsInitialized = false;
    private static volatile long lastHotReloadCheckMillis = 0L;
    private static volatile boolean dirtyNewDefaultKeys = false;

    static {
        seedDefaultTranslations();
    }

    private static void regDef(String en, String zhTw, String zhCn, String jaJp) {
        if (en != null && !en.isEmpty()) {
            DEFAULT_TRANSLATIONS.put(en, new String[]{
                    en,
                    zhTw != null ? zhTw : en,
                    zhCn != null ? zhCn : en,
                    jaJp != null ? jaJp : en
            });
        }
    }

    private static void seedDefaultTranslations() {
        // Core HUD Labels & Modes
        regDef("HNG", "飽食", "饱食", "満腹");
        regDef("FTG", "疲勞", "疲劳", "疲労");
        regDef("THR", "口渴", "口渴", "水分");
        regDef("BND", "羈絆", "羁绊", "絆");
        regDef("MOOD", "心情", "心情", "やる気");
        regDef("Awful", "極差", "极差", "絶不調");
        regDef("Bad", "不佳", "不佳", "不調");
        regDef("Normal", "普通", "普通", "普通");
        regDef("Good", "良好", "良好", "好調");
        regDef("Great", "極佳", "极佳", "絶好調");
        regDef("SPD", "速度", "速度", "速力");
        regDef("STM", "耐力", "耐力", "持久");
        regDef("PWR", "力量", "力量", "馬力");
        regDef("GUTS", "根性", "根性", "根性");
        regDef("WITS", "智力", "智力", "賢さ");
        regDef("JMP", "跳躍", "跳跃", "飛越");
        regDef("TRAINING EXP", "訓練經驗", "训练经验", "調教経験値");
        regDef("[🐎 PRACTICE]", "[🐎 單騎訓練]", "[🐎 单骑训练]", "[🐎 調教モード]");
        regDef("[🐎 RACING]", "[🐎 實戰競走]", "[🐎 实战竞走]", "[🐎 レースモード]");
        regDef("[TRAINING]", "[訓練模式]", "[训练模式]", "[調教中]");
        regDef("[STALL #%d LOCKED]", "[%d號閘廂已鎖定]", "[%d号闸厢已锁定]", "[%d番ゲートロック中]");
        regDef("[REFUSING TO MOVE!]", "[拒絕前進！]", "[拒绝前进！]", "[前進拒否！]");
        regDef("[EXHAUSTED]", "[體力耗盡]", "[体力耗尽]", "[バテ状態]");
        regDef("[PANIC!]", "[失控暴走！]", "[失控暴走！]", "[掛かり/暴走！]");
        regDef("[RAGGED BREATH]", "[呼吸紊亂]", "[呼吸紊乱]", "[息乱れ]");
        regDef("[POOL]", "[泳池訓練]", "[泳池训练]", "[プール調教]");
        regDef("[UPHILL]", "[坡道]", "[坡道]", "[坂路]");
        regDef("▶▶ GATES OPEN! TAP [W] TO BREAK! ◀◀", "▶▶ 閘門開啟！立即按 [W] 出閘！ ◀◀", "▶▶ 闸门开启！立即按 [W] 出闸！ ◀◀", "▶▶ ゲート開！[W]キーでスタート！ ◀◀");
        regDef("PERFECT START!", "完美出閘！", "完美出闸！", "ロケットスタート！");
        regDef("GOOD START!", "良好出閘！", "良好出闸！", "好スタート！");
        regDef("LATE START!", "出閘遲緩！", "出闸迟缓！", "出遅れ！");
        regDef("MISSED START! (>1.0s)", "嚴重漏閘！(>1.0秒)", "严重漏闸！(>1.0秒)", "大出遅れ！(>1.0秒)");
        regDef("▲ SPEED UP! (+0.1)", "▲ 速度提升! (+0.1)", "▲ 速度提升! (+0.1)", "▲ スピードUP! (+0.1)");
        regDef("▲ STAMINA UP! (+0.1)", "▲ 耐力提升! (+0.1)", "▲ 耐力提升! (+0.1)", "▲ スタミナUP! (+0.1)");
        regDef("▲ POWER UP! (+0.1)", "▲ 力量提升! (+0.1)", "▲ 力量提升! (+0.1)", "▲ パワーUP! (+0.1)");
        regDef("▲ GUTS UP! (+0.1)", "▲ 根性提升! (+0.1)", "▲ 根性提升! (+0.1)", "▲ 根性UP! (+0.1)");
        regDef("▲ WITS UP! (+0.1)", "▲ 智力提升! (+0.1)", "▲ 智力提升! (+0.1)", "▲ 賢さUP! (+0.1)");

        // Active Running Strategy HUD Tags
        regDef("[BIG ESCAPE]", "[大逃]", "[大逃]", "[大逃げ]");
        regDef("[BIG ESCAPE: PRESSURED]", "[大逃: 受迫]", "[大逃: 受迫]", "[大逃げ: 競り合い]");
        regDef("[UNFADING ESCAPE!]", "[不滅大逃！]", "[不灭大逃！]", "[異次元の逃亡者！]");
        regDef("[BIG ESCAPE SPURT]", "[大逃衝刺]", "[大逃冲刺]", "[大逃げスパート]");
        regDef("[FRONT: CLEAR LEAD]", "[領放: 單騎領先]", "[领放: 单骑领先]", "[逃げ: 単騎先頭]");
        regDef("[FRONT RUNNER]", "[領放]", "[领放]", "[逃げ]");
        regDef("[FRONT SPURT]", "[領放衝刺]", "[领放冲刺]", "[逃げスパート]");
        regDef("[PACE CHASER]", "[跟前]", "[跟前]", "[先行]");
        regDef("[PACE SPURT]", "[跟前衝刺]", "[跟前冲刺]", "[先行抜け出し]");
        regDef("[LATE SURGER]", "[居中]", "[居中]", "[差し]");
        regDef("[LATE SURGE!]", "[居中突圍！]", "[居中突围！]", "[差し脚炸裂！]");
        regDef("[END CLOSER]", "[後追]", "[后追]", "[追込]");
        regDef("[END CLOSER SPURT!]", "[後追強襲！]", "[后追强袭！]", "[直線一気！]");
        regDef("[RECKLESS OVERPACE!]", "[盲目搶放！]", "[盲目抢放！]", "[暴走オーバーペース！]");
        regDef("[HELD BACK / KEEN]", "[強行留後 / 搶口]", "[强行留后 / 抢口]", "[折り合い欠く]");
        regDef("[OUT OF RHYTHM]", "[節奏紊亂]", "[节奏紊乱]", "[ペース乱れ]");
        regDef("[FIGHTING BIT!]", "[嚴重搶口！]", "[严重抢口！]", "[激しい掛かり！]");
        regDef("[SEVERE OVERPACE!]", "[嚴重超速失速！]", "[严重超速失速！]", "[激しいオーバーペース！]");
        regDef("[BOXED IN]", "[受困包圍]", "[受困包围]", "[馬群包囲・前壁]");

        // Condition HUD Tags
        regDef("[DEATH'S DOOR]", "[瀕死邊緣]", "[濒死边缘]", "[危篤状態]");
        regDef("[POOR PROGNOSIS]", "[預後不良]", "[预后不良]", "[予後不良]");
        regDef("[A-FIB!]", "[心房顫動!]", "[心房颤动!]", "[心房細動!]");
        regDef("[LARYNGEAL 2x STM]", "[喘鳴症 2倍耗耐]", "[喘鸣症 2倍耗耐]", "[喘鳴症 消費2倍]");
        regDef("[LAME: 12 KM/H CAP]", "[跛行: 限速12km/h]", "[跛行: 限速12km/h]", "[跛行: 制限12km/h]");
        regDef("[FEVER]", "[發燒]", "[发烧]", "[発熱]");
        regDef("[BLEEDER/EIPH]", "[肺出血/EIPH]", "[肺出血/EIPH]", "[鼻出血/EIPH]");
        regDef("[COLIC]", "[腹痛/絞痛]", "[腹痛/绞痛]", "[疝痛]");
        regDef("[OVERWEIGHT]", "[體重過重]", "[体重过重]", "[太り気味]");
        regDef("[UNSTABLE LEGS]", "[雙腿發軟]", "[双腿发软]", "[脚元不安]");
        regDef("[RETIRED FROM INJURY]", "[因傷退役]", "[因伤退役]", "[怪我引退]");
        regDef("[BROODMARE: PREGNANT]", "[繁殖牝馬: 懷孕中]", "[繁殖母马: 怀孕中]", "[繁殖牝馬: 受胎中]");
        regDef("[BROODMARE: IN HEAT]", "[繁殖牝馬: 發情期]", "[繁殖母马: 发情期]", "[繁殖牝馬: 発情期]");
        regDef("[RETIRED BROODMARE]", "[退役繁殖牝馬]", "[退役繁殖母马]", "[引退繁殖牝馬]");
        regDef("[RETIRED STUD]", "[退役種馬]", "[退役种马]", "[引退種牡馬]");

        // Live Leaderboard & 3D Tote Board
        regDef("GATE LOAD", "入閘準備", "入闸准备", "枠入り中");
        regDef("ARMED", "準備出閘", "准备出闸", "発走準備");
        regDef("LIVE", "競走中", "比赛进行中", "走行中");
        regDef("INQUIRY", "審議中", "审议中", "審議中");
        regDef("FINISH", "衝線完賽", "冲线完赛", "ゴール");
        regDef("CONFIRMED", "確定", "确定", "確定");
        regDef("STANDBY", "待機中", "待机中", "待機中");
        regDef("Rem:", "剩餘:", "剩余:", "残り:");
        regDef("Runners", "匹出賽", "匹出赛", "頭立");
        regDef("LEAD", "領先", "领先", "先頭");
        regDef("WIN", "勝出", "胜出", "1着");
        regDef("DH", "平頭", "平头", "同着");
        regDef("Nose", "鼻位", "鼻位", "ハナ");
        regDef("Head", "頭位", "头位", "アタマ");
        regDef("Neck", "頸位", "颈位", "クビ");
        regDef("Dist", "大差", "大差", "大差");
        regDef("TURF", "草地", "草地", "芝");
        regDef("DIRT", "泥地", "泥地", "ダート");
        regDef("FIRM", "好地", "好地", "良");
        regDef("YIELDING", "黏地", "黏地", "稍重");
        regDef("SOFT", "軟地", "软地", "不良");
        regDef("STANDARD", "標準", "标准", "良");
        regDef("GOOD", "好地", "好地", "稍重");
        regDef("MUDDY", "泥濘", "泥泞", "不良");
        // Contextual Mood & Track condition keys (Disambiguated for JSON customization)
        regDef("Mood: Awful", "極差", "极差", "絶不調");
        regDef("Mood: Bad", "不佳", "不佳", "不調");
        regDef("Mood: Normal", "普通", "普通", "普通");
        regDef("Mood: Good", "良好", "良好", "好調");
        regDef("Mood: Great", "極佳", "极佳", "絶好調");

        regDef("Track: Firm", "好地", "好地", "良");
        regDef("Track: Good", "好地", "好地", "稍重");
        regDef("Track: Yielding", "黏地", "黏地", "稍重");
        regDef("Track: Soft", "爛地", "烂地", "重");
        regDef("Track: Muddy", "泥濘", "泥泞", "不良");
        regDef("Track: Heavy", "濕爛", "湿烂", "不良");
        regDef("Track: Standard", "標準", "标准", "良");

        // Race Age Requirements (Real-Life Divisions: 2yo Only, 3yo Only, 3yo+, 4yo+)
        regDef("2-Year-Olds Only", "2歲限定", "2岁限定", "2歳限定");
        regDef("3-Year-Olds Only", "3歲限定 (經典)", "3岁限定 (经典)", "3歳限定 (クラシック)");
        regDef("3-Years-Old and Up", "3歲及以上", "3岁及以上", "3歳以上");
        regDef("4-Years-Old and Up", "4歲及以上", "4岁及以上", "4歳以上");
        regDef("Age Req:", "年齡條件:", "年龄条件:", "馬齢:");
        regDef("Age Req", "年齡條件", "年龄条件", "馬齢条件");
        regDef("[BARRED — AGE RESTRICTION!]", "[參賽限制 — 年齡不合！]", "[参赛限制 — 年龄不合！]", "[出走不可 — 馬齢不一致！]");
        regDef("does not meet the age requirement for this race", "不符合此賽事的年齡條件", "不符合此赛事的年龄条件", "はこのレースの馬齢条件を満たしていません");
        regDef("does not meet the age requirement", "不符合此賽事的年齡條件", "不符合此赛事的年龄条件", "は馬齢条件を満たしていません");

        // Race Gender Requirements
        regDef("Open (All Sexes)", "無限制 (雌雄均可)", "无限制 (雌雄均可)", "混合 (性別不問)");
        regDef("Fillies & Mares Only", "雌馬限定 (牝馬)", "雌马限定 (牝马)", "牝馬限定");
        regDef("Gender Req:", "性別限制:", "性别限制:", "性別:");
        regDef("Gender Req", "性別限制", "性别限制", "性別条件");
        regDef("[BARRED — GENDER RESTRICTION!]", "[參賽限制 — 性別不合！]", "[参赛限制 — 性别不合！]", "[出走不可 — 牝馬限定！]");
        regDef("is barred from this race (Fillies & Mares Only)!", "無法參加此賽事（僅限雌馬/牝馬）！", "无法参加此赛事（仅限雌马/牝马）！", "はこのレースに出走できません（牝馬限定）！");
        regDef("is barred by gender requirement (Fillies & Mares Only)!", "受限於性別限制無法參賽（僅限雌馬/牝馬）！", "受限于性别限制无法参赛（仅限雌马/牝马）！", "は性別条件により出走できません（牝馬限定）！");

        // Race Series & Immersion Tags (Default 5 + Custom Tag Support)
        regDef("Series Tag:", "系列賽:", "系列赛:", "シリーズ:");
        regDef("Series Tag", "系列賽標籤", "系列赛标签", "シリーズ冠");
        regDef("Series: ", "系列: ", "系列: ", "シリーズ: ");
        regDef("Custom Tag...", "自訂系列標籤...", "自订系列标签...", "カスタムタグ...");
        regDef("Classic Triple Crown", "經典三冠大賽", "经典三冠大赛", "クラシック三冠");
        regDef("Triple Tiara", "雌馬三冠", "雌马三冠", "牝馬三冠");
        regDef("Grand Prix", "大獎賽", "大奖赛", "グランプリ");
        regDef("Dirt Triple Crown", "泥地三冠", "泥地三冠", "ダート三冠");
        regDef("Sprint Series", "短途系列賽", "短途系列赛", "スプリントシリーズ");

        // Consoles GUI Text
        regDef("Starter Control Console", "出閘控制台", "出闸控制台", "スターター制御コンソール");
        regDef("Assign Horse", "指派賽駒", "指派赛驹", "競走馬を枠順に割当");
        regDef("Clear Stall", "清空閘位", "清空闸位", "枠を空にする");
        regDef("Load into Gate", "入閘進入", "入闸进入", "ゲートに誘導・収容");
        regDef("START COUNTDOWN", "開始起步倒數", "开始起步倒数", "スタートカウントダウン");
        regDef("EMERGENCY EJECT", "緊急退出開閘", "紧急退出开闸", "緊急ゲート開放・退避");
        regDef("Starting Stalls", "起步閘位", "起步闸位", "発走枠");
        regDef("Nearby Horses", "附近純種馬", "附近纯种马", "付近の競走馬");
        regDef("No starting gates nearby!", "附近無起步閘！", "附近无起步闸！", "付近に発走ゲートがありません！");
        regDef("No thoroughbreds nearby", "附近無純種馬", "附近无纯种马", "付近にサラブレッドがいません");
        regDef("Gate #", "閘位 #", "闸位 #", "枠番 #");
        regDef("Empty", "空位", "空位", "空き");
        regDef("In Gate", "已入閘", "已入闸", "ゲートイン完了");
        regDef("Assigned", "已指派", "已指派", "枠順指定済");
        regDef("Unridden", "無騎師", "无骑师", "無人");
        regDef("GATES ARMED", "起步閘待命發走", "起步闸待命发走", "ゲートスタンバイ完了");
        regDef("Select stall & horse to assign, then start countdown.", "選擇閘位與賽駒指派，隨後啟動倒數。", "选择闸位与赛驹指派，随后启动倒数。", "枠と馬を選択して割当後、カウントダウンを開始してください。");
        regDef("Racing Console", "賽事中央控制台", "赛事中央控制台", "レース管理コンソール");
        regDef("Racecourse", "馬場名稱", "马场名称", "競馬場名");
        regDef("Set Name", "設定名稱", "设定名称", "名前保存");
        regDef("Course:", "馬場賽道:", "马场赛道:", "コース:");
        regDef("Building:", "測量建置中:", "测量建置中:", "測量中:");
        regDef("Save Race Base Type", "儲存賽道基礎規格", "保存赛道基础规格", "基本コース保存");
        regDef("Cancel Build", "取消測量", "取消测量", "測量中止");
        regDef("Start Course Setup", "啟動賽道測量建置", "启动赛道测量建置", "コース新設開始");
        regDef("Base Types", "賽道基礎規格", "赛道基础规格", "コース種別");
        regDef("Races", "賽事列表", "赛事列表", "レース一覧");
        regDef("Stalls", "起跑閘位", "起跑闸位", "発走枠順");
        regDef("New Race", "新設賽事", "新设赛事", "新レース");
        regDef("START RACE", "啟動開賽", "启动开赛", "レース発走");
        regDef("Save Base", "儲存基礎賽道", "保存基础赛道", "基本コース保存");
        regDef("Edit Base", "編輯基礎賽道", "编辑基础赛道", "コース編集");
        regDef("Delete Base", "刪除基礎賽道", "删除基础赛道", "コース削除");
        regDef("Create Race", "新設賽事", "新设赛事", "新レース作成");
        regDef("Edit Race", "編輯賽事", "编辑赛事", "レース編集");
        regDef("Delete Race", "刪除賽事", "删除赛事", "レース削除");
        regDef("Save Race Changes", "儲存賽事變更", "保存赛事变更", "変更を保存");
        regDef("Cancel Active Race", "中止進行中賽事", "中止进行中赛事", "レース中止");
        regDef("Fanfare Folder", "號角音樂資料夾", "号角音乐文件夹", "ファンファーレフォルダ");
        regDef("Refresh", "重新整理", "重新整理", "更新");
        regDef("Close", "關閉", "关闭", "閉じる");
        regDef("Race Settings", "賽事基本設定", "赛事基本设定", "レース基本設定");
        regDef("G1 Special FX Studio", "G1 演出特效工作室", "G1 演出特效工作室", "G1 特殊演出スタジオ");
        regDef("Race Name", "賽事名稱", "赛事名称", "レース名");
        regDef("1st Place Purse", "頭馬獎金", "头马奖金", "1着本賞金");
        regDef("Req. Earnings", "參賽門檻獎金", "参赛门槛奖金", "出走条件収得賞金");
        regDef("Fanfare", "開賽號角", "开赛号角", "ファンファーレ");
        regDef("Theme Music", "賽事主題曲", "赛事主题曲", "レースBGM");
        regDef("Spurt Music", "最後衝刺曲", "最后冲刺曲", "ラストスパートBGM");
        regDef("Back", "返回", "返回", "戻る");

        // Status, Injury, Poor Prognosis & Death Messages
        regDef("CATASTROPHIC FRACTURE — POOR PROGNOSIS!", "粉碎性骨折 — 預後不良！", "粉碎性骨折 — 预后不良！", "粉砕骨折 — 予後不良！");
        regDef("%s suffered a catastrophic leg fracture, collapsed to the ground, and cannot move! Mood dropped to Awful.",
                "%s 遭遇了粉碎性腿部骨折，痛苦倒地無法動彈！心情跌至極差。",
                "%s 遭遇了粉碎性腿部骨折，痛苦倒地无法动弹！心情跌至极差。",
                "%s は脚の粉砕骨折を発症し、その場に倒れ込んで動けなくなりました！やる気が絶不調に転落しました。");
        regDef("DEATH NOTIFICATION", "死亡通知", "死亡通知", "死亡通知");
        regDef("%s has passed away - in excruciating pain. You did this to yourself.",
                "%s 在極度劇痛中離世了。這是你自找的。",
                "%s 在极度剧痛中离世了。这是你自找的。",
                "%s は激痛に苦しみながら息を引き取りました。自業自得です。");
        regDef("PASSED ON", "安詳離世", "安详离世", "旅立ち");
        regDef("Despite all your efforts to save it, %s couldn't recover and passed away, with less pain than it was originally. You have tried your best, do not feel bad.",
                "儘管你傾盡全力救治，%s 仍未能康復而離世，但比起最初少受了許多痛苦。你已經盡力了，請不要太難過。",
                "尽管你竭尽全力救治，%s 仍未能康复而离世，但比起最初减轻了许多痛苦。你已经尽力了，请不要太难过。",
                "治療の甲斐なく、%s は回復に至らず息を引き取りました。しかし当初よりも苦痛は和らいでいました。全力を尽くしたのです、自分を責めないでください。");
        regDef("%s has passed away peacefully, at the age of %d. It will forever be remembered.",
                "%s 於 %d 歲高齡安詳長眠。牠的傳奇將永遠被銘記。",
                "%s 于 %d 岁高龄安详长眠。它的传奇将永远被铭记。",
                "%s は享年 %d 歳、天寿を全うし安らかに旅立ちました。その名は永遠に記憶されるでしょう。");
        regDef("Fracture Healed!", "骨折痊癒！", "骨折痊愈！", "骨折完治！");
        regDef("has made a complete recovery (80% roll) from its Non-Catastrophic Fracture and is ready to resume full training!",
                "輕微骨折已完全康復（80% 機率判定成功），準備好重返全速訓練與賽事！",
                "轻微骨折已完全康复（80% 概率判定成功），准备好重返全速训练与赛事！",
                "軽微な骨折から完全回復しました（80%判定）。本格調教とレースへの復帰が可能です！");
        regDef("Fracture Healed — Permanent Career End", "骨折癒合 — 永久退役繁殖", "骨折愈合 — 永久退役繁殖", "骨折治癒・競走馬引退");
        regDef("healed from its Non-Catastrophic Fracture (20% roll), but can no longer run at racing speed! It has been retired for breeding.",
                "輕微骨折已癒合（20% 殘留影響判定），但無法再以競速奔馳！現已永久轉為種馬/繁殖牝馬退役。",
                "轻微骨折已愈合（20% 残留影响判定），但无法再以竞速奔驰！现已永久转为种马/繁殖母马退役。",
                "軽微な骨折は治癒しましたが（20%判定）、競走速度での走行は不可能となりました。繁殖馬として引退しました。");
        regDef("is lying down in critical condition and cannot be mounted!",
                "處於預後不良危重狀態臥地，無法騎乘！",
                "处于预后不良危重状态卧地，无法骑乘！",
                "は予後不良の重体で横たわっており、騎乗できません！");
        regDef("INJURY: %s!", "受傷: %s！", "受伤: %s！", "故障発症: %s！");
        regDef("suffered a %s and is now Lame (%.1f MC days recovery; Mood dropped to Awful; Training & Racing locked out)!",
                "遭遇了 %s，現已跛行（需休養 %.1f 個遊戲日；心情跌至極差；暫停訓練與參賽）！",
                "遭遇了 %s，现已跛行（需休养 %.1f 个游戏日；心情跌至极差；暂停训练与参赛）！",
                "により %s を発症し跛行状態となりました（完治まで約 %.1f 日；やる気絶不調；調教・出走停止）！");

        regDef("RACE TIME", "走破時間", "比赛用时", "タイム");
        regDef("RUNNERS", "出賽馬匹", "出赛马匹", "出走頭数");
        regDef("WEATHER", "天氣", "天气", "天候");
        regDef("FINE", "晴朗", "晴朗", "晴");
        regDef("RAIN", "雨天", "雨天", "雨");
        regDef("STORM", "暴風雨", "暴风雨", "暴風雨");
        regDef("WINNER", "頭馬", "头马", "勝馬");
        regDef("LEADER", "領放馬", "领放马", "先頭");
        regDef("Awaiting Race", "等待賽事開始", "等待赛事开始", "レース待機中");
        regDef("TRACKSIDE RESULTS TOTE BOARD", "賽馬場賽果顯示板", "赛马场赛果显示板", "レース着順掲示板");
        regDef("1st", "1着", "第1", "1着");
        regDef("2nd", "2着", "第2", "2着");
        regDef("3rd", "3着", "第3", "3着");
        regDef("4th", "4着", "第4", "4着");
        regDef("5th", "5着", "第5", "5着");

        // Stat Card & Pedigree & Screens
        regDef("Speed", "速度", "速度", "スピード");
        regDef("Stamina", "耐力", "耐力", "スタミナ");
        regDef("Power", "力量", "力量", "パワー");
        regDef("Guts", "根性", "根性", "根性");
        regDef("Wits", "智力", "智力", "賢さ");
        regDef("TRACK APTITUDE", "場地適性", "场地适性", "馬場適性");
        regDef("DISTANCE APTITUDE", "距離適性", "距离适性", "距離適性");
        regDef("RUNNING STRATEGY", "跑法腳質", "跑法脚质", "脚質適性");
        regDef("HEALTH, AILMENTS & INJURIES", "健康、疾病與傷患", "健康、疾病与伤患", "健康・疾病・故障状態");
        regDef("GENETICS, TRAITS & BREEDING", "遺傳基因、特性與配種", "遗传基因、特性与配种", "遺伝・特性・繁殖情報");
        regDef("CAREER RECORD & FINISHES", "生涯戰績與賽果", "生涯战绩与赛果", "競走成績・戦績");
        regDef("Starts", "出賽", "出赛", "戦績");
        regDef("Total Earnings", "總獎金", "总奖金", "獲得賞金");
        regDef("Pedigree", "血統表", "血统表", "血統表");
        regDef("Studbook", "血統書", "血统书", "血統書");
        regDef("Close", "關閉", "关闭", "閉じる");
        regDef("Cancel", "取消", "取消", "キャンセル");
        regDef("Weighted Pad: ON", "負重鞍墊: 開啟", "负重鞍垫: 开启", "重量パッド: ON");
        regDef("Weighted Pad: OFF", "負重鞍墊: 關閉", "负重鞍垫: 关闭", "重量パッド: OFF");
        regDef("Blinkers: ON", "眼罩: 開啟", "眼罩: 开启", "ブリンカー: ON");
        regDef("Blinkers: OFF", "眼罩: 關閉", "眼罩: 关闭", "ブリンカー: OFF");
        regDef("Steward Race Results Review", "競賽董事賽果審議面板", "竞赛董事赛果审议面板", "裁決委員・着順審議パネル");
        regDef("Accept & Confirm Official Results", "確認並公佈正式賽果 (派發獎金與戰績)", "确认并公布正式赛果 (派发奖金与战绩)", "着順確定・賞金および戦績を反映");
        // Disambiguated Mood / Track Keys & Console / Status Localization
        regDef("Track: Fast", "快地", "快地", "良");
        regDef("Track: Sloppy", "泥濘", "泥泞", "不良");
        regDef("Poor Prognosis", "預後不良", "预后不良", "予後不良");
        regDef("is lying down in critical condition and cannot be mounted! (Transport via Lead, Boat, or Minecart)", "處於預後不良危重狀態臥地，無法騎乘！（請使用韁繩牽引、乘船或礦車運送）", "处于预后不良危重状态卧地，无法骑乘！（请使用缰绳牵引、乘船或矿车运送）", "は予後不良の重体で横たわっており、騎乗できません！（リード、ボート、またはトロッコで移動させてください）");
        regDef("+ Create Race Base Type", "+ 新增賽道規格", "+ 新增赛道规格", "+ コース規格を作成");
        regDef("Test Race", "測試賽", "测试赛", "テスト走");
        regDef("+ New Race", "+ 新賽事", "+ 新赛事", "+ 新規レース");
        regDef("Del", "刪除", "删除", "削除");
        regDef("Start Race", "開始賽事", "开始赛事", "レース発走");
        regDef("Edit", "編輯", "编辑", "編集");
        regDef("Assign", "指派", "指派", "割当");
        regDef("Clear", "清空", "清空", "解除");
        regDef("Track Type: ", "場地類型: ", "场地类型: ", "馬場種別: ");
        regDef("Gate Direction: ", "閘門朝向: ", "闸门朝向: ", "ゲート向き: ");
        regDef("[⇄ Flip 180°]", "[⇄ 翻轉 180°]", "[⇄ 翻转 180°]", "[⇄ 180°反転]");
        regDef("Save Base Changes", "儲存賽道變更", "保存赛道变更", "コース変更を保存");
        regDef("[⚙ Race Settings]", "[⚙ 賽事設定]", "[⚙ 赛事设定]", "[⚙ レース設定]");
        regDef("⚙ Race Settings", "⚙ 賽事設定", "⚙ 赛事设定", "⚙ レース設定");
        regDef("[✨ G1 Special FX Studio]", "[✨ G1 特效工作室]", "[✨ G1 特效工作室]", "[✨ G1 演出スタジオ]");
        regDef("✨ G1 Special FX Studio", "✨ G1 特效工作室", "✨ G1 特效工作室", "✨ G1 演出スタジオ");
        regDef("Base: ", "賽道: ", "赛道: ", "コース: ");
        regDef("Grade: ", "級別: ", "级别: ", "格付: ");
        regDef("Req: ", "獎金門檻: ", "奖金门槛: ", "収得条件: ");
        regDef("None ($0)", "無 ($0)", "无 ($0)", "なし ($0)");
        regDef("Age: ", "馬齡: ", "马龄: ", "馬齢: ");
        regDef("Sex: ", "性別: ", "性别: ", "性別: ");
        regDef("None", "無", "无", "なし");
        regDef("Series Tag: ", "系列標籤: ", "系列标签: ", "シリーズ: ");
        regDef("Theme: ", "配樂: ", "配乐: ", "BGM: ");
        regDef("Spurt: ", "衝刺: ", "冲刺: ", "スパート: ");
        regDef("Official Racing Console", "官方賽事控制台", "官方赛事控制台", "公式レーシングコンソール");
        regDef("Yes", "是", "是", "済");
        regDef("No", "否", "否", "未");
        regDef("No Race Base Types configured on this console yet.", "此控制台尚未配置任何賽道規格。", "此控制台尚未配置任何赛道规格。", "このコンソールにはまだコース規格が設定されていません。");
        regDef("Click [+ Create Race Base Type] above to receive a Checkpoint Marker!", "點擊上方 [+ 新增賽道規格] 領取檢查點標記器！", "点击上方 [+ 新增赛道规格] 领取检查点标记器！", "上の [+ コース規格を作成] を押してマーカーを受け取ってください！");
        regDef("Use [M] to switch between Starting Point, Checkpoint Line (2 clicks), and Finish Line modes.", "按 [M] 切換起跑閘位、檢查線(點擊兩端)與終點線模式。", "按 [M] 切换起跑闸位、检查线(点击两端)与终点线模式。", "[M]キーで発走枠・チェックライン(2点指定)・決勝線モードを切替えます。");
        regDef("Gates: ", "閘向: ", "闸向: ", "枠向: ");
        regDef("No races yet.", "尚無賽事。", "尚无赛事。", "レース未登録。");
        regDef("Click '+ New Race'!", "請點擊「+ 新賽事」！", "请点击“+ 新赛事”！", "「+ 新規レース」を押してください！");
        regDef("Open", "空位", "空位", "空き");
        regDef("BARRED", "不符資格", "不符资格", "出走不可");
        regDef("Edit Race Base Type", "編輯賽道規格", "编辑赛道规格", "コース規格の編集");
        regDef("Save New Race Base Type", "儲存新賽道規格", "保存新赛道规格", "新規コース規格の保存");
        regDef("Course Layout: §e%d Stalls §7| §b%d Checkpoints §7| §dFinish Line Ready", "賽道配置: §e%d 閘位 §7| §b%d 檢查點 §7| §d終點線就緒", "赛道配置: §e%d 闸位 §7| §b%d 检查点 §7| §d终点线就绪", "コース構成: §e%d ゲート §7| §b%d チェックポイント §7| §d決勝線完了");
        regDef("Race Distance: ", "賽道途程: ", "赛道途程: ", "コース距離: ");
        regDef("Structured Base Race Type Name Preview:", "賽道規格名稱預覽:", "赛道规格名称预览:", "コース規格プレビュー:");
        regDef("Edit Race (", "編輯賽事 (", "编辑赛事 (", "レース編集 (");
        regDef("Create New Race (", "建立新賽事 (", "创建新赛事 (", "新規レース作成 (");
        regDef("1st $:", "頭馬$:", "头马$:", "1着$:");
        regDef("Purse", "獎金", "奖金", "賞金");
        regDef("Min $100 & multiple of 10 required!", "須滿 $100 且為 10 的倍數！", "须满 $100 且为 10 的倍数！", "最低$100かつ10の倍数が必要です！");
        regDef("Edit Jockey Silk", "編輯綵衣", "编辑彩衣", "勝負服を編集");
        regDef("Official Horse Owner & Farm Profile (/tb owner)", "馬主與牧場檔案 (/tb owner)", "马主与牧场档案 (/tb owner)", "馬主・牧場プロフィール (/tb owner)");
        regDef("Horse Owner License Registration (/tb owner)", "馬主牌照登記 (/tb owner)", "马主执照登记 (/tb owner)", "馬主ライセンス登録 (/tb owner)");
        regDef("Sort: Earnings", "排序: 獎金", "排序: 奖金", "順: 賞金");
        regDef("Sort: Wins", "排序: 勝場", "排序: 胜场", "順: 勝利数");
        regDef("Sort: HP%", "排序: 血統HP%", "排序: 血统HP%", "順: 血統HP%");
        regDef("Sort: Name A-Z", "排序: 馬名", "排序: 马名", "順: 馬名");
        regDef("All", "全部", "全部", "すべて");
        regDef("Active", "現役", "现役", "現役");
        regDef("Studs", "種馬", "种马", "種牡馬");
        regDef("Broodmares", "繁殖牝馬", "繁殖母马", "繁殖牝馬");
        regDef("Youngsters", "幼駒", "幼驹", "若駒");
        regDef("Deceased", "已故", "已故", "死去");
        regDef("Official Thoroughbred Studbook", "純血馬官方血統書", "纯血马官方血统书", "公式サラブレッド血統書");
        regDef("All Crops", "全部世代", "全部世代", "全世代");
        regDef("Restore", "恢復資格", "恢复资格", "失格解除");
        regDef("DSQ", "取消資格", "取消资格", "失格処分");
        regDef("Official Results Confirmed & Paid Out", "正式賽果已確認並派發獎金", "正式赛果已确认并派发奖金", "公式着順確定・賞金支払済");
        regDef("Lang Folder", "翻譯資料夾", "翻译文件夹", "翻訳フォルダ");
        regDef("INQUIRY / PENDING CONFIRMATION", "審議中 / 待確認", "审议中 / 待确认", "審議中・未確定");
        regDef("Midnight Digital LED", "午夜數碼LED", "午夜数码LED", "ミッドナイトLED");
        regDef("Prestige Gold & Obsidian", "尊尚黑金", "尊尚黑金", "プレステージゴールド");
        regDef("Classic Racecourse Emerald", "經典翠綠賽馬場", "经典翠绿赛马场", "クラシックターフグリーン");
        regDef("Trackside Tote Board", "賽果顯示板", "赛果显示板", "着順掲示板");
        regDef("Theme:", "主題:", "主题:", "テーマ:");
        regDef("blocks", "方塊", "方块", "ブロック");
        regDef("Multi-Block Screen Active", "多方塊大螢幕運作中", "多方块大屏幕运行中", "マルチブロックスクリーン稼働中");
        regDef("Sneak + Right-Click to change theme", "潛行+右鍵切換主題", "潜行+右键切换主题", "スニーク+右クリックでテーマ切替");
        regDef("Semaphore Flag", "司閘員信號旗", "司闸员信号旗", "発走委員の赤旗");
        regDef("Left-click the air 3 times to wave the flag and start the race!", "對空左鍵揮旗 3 次即可奏響號角並開始賽事！", "对空左键挥旗 3 次即可奏响号角并开始赛事！", "空中で左クリックを3回行って旗を振り、ファンファーレと発走を開始します！");

        // Surfaces & Track Conditions
        regDef("Dirt", "泥地", "泥地", "ダート");
        regDef("Turf", "草地", "草地", "芝");

        // Pedigree & Cross Variants
        regDef("Outcross", "異系交配 (Outcross)", "异系交配 (Outcross)", "アウトブリード");
        regDef("Foundation (Outcross)", "基礎名驥 (異系)", "基础名骥 (异系)", "基礎馬 (アウトブリード)");
        regDef("Outcross (No Inbreeding)", "異系交配 (無近親交配)", "异系交配 (无近亲交配)", "アウトブリード (近親交配なし)");
        regDef("Close Inbreeding: %s (%dx%d)", "近親交配: %s (%dx%d)", "近亲交配: %s (%dx%d)", "インブリード: %s (%dx%d)");
        regDef("Linebred Cross: %s (%dx%d)", "血線交配: %s (%dx%d)", "血线交配: %s (%dx%d)", "ラインブリード: %s (%dx%d)");
        regDef("Cross", "交配模式", "交配模式", "配合");
        regDef("Cross:", "交配模式:", "交配模式:", "配合:");
        regDef("Hereditary Potential", "遺傳潛能", "遗传潜能", "遺伝ポテンシャル");
        regDef("Hereditary Potential:", "遺傳潛能:", "遗传潜能:", "遺伝ポテンシャル:");
        regDef("Linebred Shared Ancestor", "同血線重疊祖先", "同血线重叠祖先", "ラインブリード共通祖先");
        regDef("4-Generation Pedigree Tree", "四代血統譜", "四代血统谱", "四代血統表");

        // Euthanasia, Veterinary & Prognosis Messages
        regDef("%s lives for another day - but on borrowed time. It is in its best interest you euthanize it with /tb euthanize.",
                "%s 多活了一天——但已時日無多。為了牠的福利著想，建議使用 /tb euthanize 進行人道安樂死。",
                "%s 多活了一天——但已时日无多。为了它的福利着想，建议使用 /tb euthanize 进行人道安乐死。",
                "%s は命をつなぎ止めましたが、余命は幾ばくもありません。苦痛を長引かせないため、/tb euthanize による安楽死を推奨します。");
        regDef("Perhaps by some miracle, %s has recovered! And it is ready to continue running on the tracks!",
                "奇蹟出現了，%s 竟然完全康復了！牠已經準備好重返賽場奔馳！",
                "奇迹出现了，%s 竟然完全康复了！它已经准备好重返赛场奔驰！",
                "奇跡が起きました！%s は見事に回復し、再び競馬場を走る準備が整いました！");
        regDef("Perhaps by some miracle, %s has recovered! However, it can no longer run as fast as it was - the injury has completely ruined its legs.",
                "奇蹟出現了，%s 撿回了一命！然而牠再也無法像從前那樣飛馳——這場重傷徹底摧毀了牠的腿部。",
                "奇迹出现了，%s 捡回了一命！然而它再也无法像从前那样飞驰——这场重伤彻底摧毁了它的腿部。",
                "奇跡的に %s は一命を取り留めました！しかし脚の負傷が深刻なため、以前のように速く走ることはもうできません。");
        regDef("%s has developed Laminitis while immobilized! Its daily death rate has risen to 60% (living rate dropped to 39%).",
                "%s 在無法動彈期間併發了蹄葉炎！每日死亡機率攀升至 60%（存活率驟降至 39%）。",
                "%s 在无法动弹期间并发了蹄叶炎！每日死亡机率攀升至 60%（存活率骤降至 39%）。",
                "起立不能の間に %s は蹄葉炎を発症しました！日ごとの死亡率が 60% に上昇しました（生存率は 39% に低下）。");
        regDef("[Laminitis Developed]", "[併發蹄葉炎]", "[并发蹄叶炎]", "[蹄葉炎発症]");
        regDef("Could not find that Thoroughbred! Stand near it or specify its name: /tb euthanize [horse name]",
                "找不到該純種馬！請靠近該馬匹或指定其名稱：/tb euthanize [馬匹名稱]",
                "找不到该纯种马！请靠近该马匹或指定其名称：/tb euthanize [马匹名称]",
                "該当するサラブレッドが見つかりません！近くに立つか馬名を指定してください: /tb euthanize [馬名]");
        regDef("That horse does not belong to you!", "這匹馬不屬於你！", "这匹马不属于你！", "その馬の所有者ではありません！");
        regDef("/tb euthanize can ONLY be used on a horse diagnosed with a Poor Prognosis! (%s does not have a Poor Prognosis.)",
                "/tb euthanize 只能用於確診為預後不良（Poor Prognosis）的馬匹！（%s 未被診斷為預後不良。）",
                "/tb euthanize 只能用于确诊为预后不良（Poor Prognosis）的马匹！（%s 未被诊断为预后不良。）",
                "/tb euthanize は予後不良と診断された馬にのみ使用できます！（%s は予後不良ではありません。）");
        regDef("[Humane Euthanasia] %s closed its eyes peacefully and was humanely laid to rest, free from pain. Its pedigree remains archived in the Studbook.",
                "[人道安樂死] %s 安詳地閉上了雙眼，脫離了病痛長眠。其血統紀錄仍永久保存在血統書中。",
                "[人道安乐死] %s 安详地闭上了双眼，脱离了病痛长眠。其血统纪录仍永久保存在血统书中。",
                "[安楽死処置] %s は苦痛から解放され、安らかに永遠の眠りにつきました。血統書にはその血筋が永久に記録されます。");
        regDef("The vet determines that the horse is beyond saving unless a miracle happens, and euthanizing it is the most humane option - you will not be charged for anything.",
                "獸醫判定該馬匹除非發生奇蹟否則已無力回天，人道安樂死是最仁慈的選擇——本次看診不收取任何費用。",
                "兽医判定该马匹除非发生奇迹否则已无力回天，人道安乐死是最仁慈的选择——本次看诊不收取任何费用。",
                "獣医師の見立てでは、奇跡が起きない限り回復の見込みはありません。安楽死が最も人道的な選択肢です——診察費は請求されません。");

        // Veterinary Checkup & Treatments
        regDef("Ride your Thoroughbred, stand near it, or specify its name: /tb vet [horse name]",
                "請騎乘純種馬、靠近馬匹，或指定其名稱：/tb vet [馬匹名稱]",
                "请骑乘纯种马、靠近马匹，或指定其名称：/tb vet [马匹名称]",
                "サラブレッドに騎乗するか近くに立つか、馬名を指定してください: /tb vet [馬名]");
        regDef("You can only call the vet for a horse that you own!",
                "你只能為自己擁有的馬匹呼叫獸醫！",
                "你只能为自己拥有的马匹呼叫兽医！",
                "自身が所有する馬にのみ獣医を呼ぶことができます！");
        regDef("Where are you going to find a vet on the racetrack?",
                "在賽道競賽中你要去哪裡找獸醫？",
                "在赛道竞赛中你要去哪里找兽医？",
                "レース中のコース上で獣医を呼ぶことはできません！");
        regDef("[Veterinary Checkup]", "[獸醫看診]", "[兽医看诊]", "[獣医診察]");
        regDef("The vet examined %s: its %s is already under veterinary rehab (%.1f MC days remaining). No charge!",
                "獸醫為 %s 進行了檢查：其 %s 已在接受獸醫康復治療中（尚餘 %.1f 個遊戲日）。本次不收費！",
                "兽医为 %s 进行了检查：其 %s 已在接受兽医康复治疗中（尚余 %.1f 个游戏日）。本次不收费！",
                "獣医師が %s を診察しました: %s はすでに治療・リハビリ中です（残り %.1f MC日）。診察料は無料です！");
        regDef("The vet notes that %s has Unstable Legs from supercritical Fatigue (%.0f%%). Let the horse rest, brush it, or ice its legs to bring Fatigue below 80%%! No charge.",
                "獸醫提醒：%s 因極度疲勞（%.0f%%）出現雙腿不穩。請讓馬匹充分休息、刷毛或冰敷雙腿，將疲勞降至 80%% 以下！本次不收費。",
                "兽医提醒：%s 因极度疲劳（%.0f%%）出现双腿不稳。请让马匹充分休息、刷毛或冰敷双腿，将疲劳降至 80%% 以下！本次不收费。",
                "獣医師のメモ: %s は過度の疲労（%.0f%%）により脚元が不安定になっています。休養させるか、ブラッシングやアイシングで疲労を80%%未満まで回復させてください！無料です。");
        regDef("The vet examined %s and gave it a clean bill of health! ($0 charged)",
                "獸醫檢查了 %s，確認其完全健康！（不收取費用）",
                "兽医检查了 %s，确认其完全健康！（不收取费用）",
                "獣医師が %s を診察し、健康と診断しました！（診察料 $0）");
        regDef("[Insufficient Funds]", "[資金不足]", "[资金不足]", "[所持金不足]");
        regDef("Veterinary treatment for %s (%s) costs $%,d, but your balance is only $%,d!",
                "為 %s（%s）進行獸醫治療需花費 $%,d，但你的帳戶餘額僅有 $%,d！",
                "为 %s（%s）进行兽医治疗需花费 $%,d，但你的账户余额仅有 $%,d！",
                "%s（%s）の獣医治療費は $%,d ですが、残高が $%,d しかありません！");
        regDef("[Veterinary Treatment Complete!]", "[獸醫治療完成！]", "[兽医治疗完成！]", "[獣医治療完了！]");
        regDef("Paid $%,d to treat %s (%s)! Remaining Balance: $%,d.",
                "已支付 $%,d 為 %s 治療（%s）！剩餘帳戶餘額：$%,d。",
                "已支付 $%,d 为 %s 治疗（%s）！剩余账户余额：$%,d。",
                "%s の治療（%s）に $%,d を支払いました！残り残高: $%,d。");

        // Health, Daily Recoveries & Life Stage Messages
        regDef("[DEATH'S DOOR]", "[瀕死垂危]", "[濒死垂危]", "[臨死の淵]");
        regDef("%s dropped to the floor, it's best if you go and visit it, it might not be here soon.",
                "%s 癱倒在地動彈不得，建議你盡快前去探望，牠可能時日不多了。",
                "%s 瘫倒在地动弹不得，建议你尽快前去探望，它可能时日不多了。",
                "%s が倒れて動けなくなりました。まもなく最期を迎えるかもしれません、早く会いに行ってあげてください。");
        regDef("[Recovered]", "[已康復]", "[已康复]", "[回復]");
        regDef("%s's post-race Lameness has cleared after a day of rest!",
                "%s 的賽後跛行在休息一天後已經完全痊癒！",
                "%s 的赛后跛行在休息一天后已经完全痊愈！",
                "%s のレース後の跛行は1日の休養で完治しました！");
        regDef("%s's Fever broke naturally after resting!",
                "%s 的發燒在休養後自然退燒康復！",
                "%s 的发烧在休养后自然退烧康复！",
                "%s の発熱は休養により自然に回復しました！");
        regDef("%s's lungs have cleared from EIPH after resting!",
                "%s 的肺出血（EIPH）在休養後已完全平復！",
                "%s 的肺出血（EIPH）在休养后已完全平复！",
                "%s の肺出血（EIPH）は休養により回復しました！");
        regDef("[Clingy Lonely]", "[黏人孤單]", "[粘人孤单]", "[さびしがり]");
        regDef("%s missed its owner's attention today and lost -8 Mood!",
                "%s 今天因為缺少主人的關心陪伴而情緒低落，心情 -8！",
                "%s 今天因为缺少主人的关心陪伴而情绪低落，心情 -8！",
                "%s は今日オーナーの愛情不足を感じて寂しがり、やる気が -8 低下しました！");
        regDef("%s returned to its ideal racing weight overnight!",
                "%s 經過一整夜已恢復理想的競賽體態體重！",
                "%s 经过一整夜已恢复理想的竞赛体态体重！",
                "%s は夜の間に理想的な競走馬体重へと戻りました！");
        regDef("[Injury Healed!]", "[傷勢痊癒！]", "[伤势痊愈！]", "[負傷完治！]");
        regDef("%s has made a 100% full recovery from its Hoof Crack!",
                "%s 的蹄裂已 100% 完全康復！",
                "%s 的蹄裂已 100% 完全康复！",
                "%s の裂蹄は100%完全に回復しました！");
        regDef("%s has made a full recovery from its Suspensory Ligament Strain!",
                "%s 的懸韌帶拉傷已完全康復！",
                "%s 的悬韧带拉伤已完全康复！",
                "%s の繋靭帯炎は完全に回復しました！");
        regDef("[Healed with Scarring]", "[癒合但遺留後遺症]", "[愈合但遗留后遗症]", "[治癒・後遺症あり]");
        regDef("%s recovered from its Suspensory Ligament Strain, but suffered a slight permanent drop (-4.0) in Speed and Power.",
                "%s 的懸韌帶拉傷已癒合，但遺留了輕微後遺症，速度與力量永久下降 -4.0。",
                "%s 的悬韧带拉伤已愈合，但遗留了轻微后遗症，速度与力量永久下降 -4.0。",
                "%s の繋靭帯炎は治癒しましたが、後遺症によりスピードとパワーが永久に -4.0 低下しました。");
        regDef("[Tendon Healed!]", "[屈腱痊癒！]", "[屈腱痊愈！]", "[屈腱炎完治！]");
        regDef("%s has made a full recovery (75% roll) from its Bowed Tendon with no stat loss!",
                "%s 的屈腱炎已完全康復（75% 機率判定成功），能力值毫無損失！",
                "%s 的屈腱炎已完全康复（75% 概率判定成功），能力值毫无损失！",
                "%s の屈腱炎は完全回復しました（75%判定成功）、能力低下はありません！");
        regDef("[Tendon Scarring]", "[屈腱纖維化疤痕]", "[屈腱纤维化疤痕]", "[屈腱炎・瘢痕化]");
        regDef("%s recovered from its Bowed Tendon (25% roll), but suffered a permanent drop (-5.0) in Speed and Power!",
                "%s 的屈腱炎雖已癒合（25% 判定），但遺留了纖維化疤痕，速度與力量永久下降 -5.0！",
                "%s 的屈腱炎虽已愈合（25% 判定），但遗留了纤维化疤痕，速度与力量永久下降 -5.0！",
                "%s の屈腱炎は治癒しましたが（25%判定）、瘢痕化によりスピードとパワーが永久に -5.0 低下しました！");
        regDef("[Breeding Season — In Heat]", "[繁殖期 — 發情期]", "[繁殖期 — 发情期]", "[繁殖期・発情期]");
        regDef("%s has entered her In Heat cycle and is ready to be bred!",
                "%s 已進入發情期，隨時可以進行配種！",
                "%s 已进入发情期，随时可以进行配种！",
                "%s は発情期に入り、交配の準備が整いました！");
        regDef("[Love Mode — Breeding Ritual]", "[求偶愛心模式 — 配種儀式]", "[求偶爱心模式 — 配种仪式]", "[求愛モード・交配の儀]");
        regDef("%s and %s have entered Love Mode and are courting!",
                "%s 與 %s 已進入求偶愛心模式並開始親暱！",
                "%s 与 %s 已进入求偶爱心模式并开始亲昵！",
                "%s と %s が求愛モードに入り、寄り添っています！");
        regDef("[Yearling (1 Equine Year Old)!]", "[週歲馬 (1 歲馬)！]", "[周岁马 (1 岁马)！]", "[当歳・1歳馬成長！]");
        regDef("%s has reached 1 Equine Year old and grown into a Yearling! (At 2 Equine Years old, it will be eligible for the Thoroughbred Registry Form, riding, training, and racing!)",
                "%s 已滿 1 馬齡並成長為週歲馬！（滿 2 馬齡時即可使用純種馬登記表註冊、騎乘、調教及參賽！）",
                "%s 已满 1 马龄并成长为周岁马！（满 2 马龄时即可使用纯种马登记表注册、骑乘、调教及参赛！）",
                "%s は馬齢1歳を迎え、1歳馬へと成長しました！（2歳になると血統登録表での登録、騎乗、調教、レース出走が可能になります！）");
        regDef("[2 Equine Years Old — Ready to Train & Race!]", "[2 馬齡成年 — 準備調教與參賽！]", "[2 马龄成年 — 准备调教与参赛！]", "[馬齢2歳・調教＆デビュー可能！]");
        regDef("%s has reached 2 Equine Years old! It can now be officially registered with a Thoroughbred Registry Form, mounted, trained, and entered into races!",
                "%s 已滿 2 馬齡！現在可以使用純種馬登記表進行官方登記註冊、正式騎乘、調教及報名出賽！",
                "%s 已满 2 马龄！现在可以使用纯种马登记表进行官方登记注册、正式骑乘、调教及报名出赛！",
                "%s は馬齢2歳に達しました！血統登録表による公式登録、騎乗、本格調教、そしてレースへの出走が可能になりました！");


    }

    public static java.util.List<java.io.File> getTranslationDirectories() {
        java.util.List<java.io.File> dirs = new java.util.ArrayList<>();
        try {
            java.io.File cwd = new java.io.File(".").getCanonicalFile();
            java.io.File cwdTrans = new java.io.File(cwd, "aiothoroughbred_translations");
            dirs.add(cwdTrans);
            if (cwd.getParentFile() != null) {
                java.io.File parent = cwd.getParentFile();
                if (new java.io.File(parent, "build.gradle").isFile() || new java.io.File(parent, "src/main/resources").isDirectory()) {
                    java.io.File parentTrans = new java.io.File(parent, "aiothoroughbred_translations");
                    if (!dirs.contains(parentTrans)) {
                        dirs.add(parentTrans);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            String userHome = System.getProperty("user.home");
            if (userHome != null && !userHome.isBlank()) {
                java.io.File desktopProj = new java.io.File(userHome, "Desktop/AIOThoroughbredMod");
                if (desktopProj.isDirectory()) {
                    java.io.File dTrans = new java.io.File(desktopProj, "aiothoroughbred_translations");
                    if (!dirs.contains(dTrans)) {
                        dirs.add(dTrans);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return dirs;
    }

    private static boolean isLikelyMojibake(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x00C0 && c <= 0x00FF && c != 0x00D7) {
                return true;
            }
            if (c == 0xFFFD) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRemovedLegacyTranslationKey(String k) {
        if (k == null) return true;
        return "Racecourse Turf 2400m".equals(k)
                || "GOOD".equals(k)
                || "Good".equals(k)
                || "Open (2yo+)".equals(k)
                || "Spring Tenno".equals(k)
                || "Autumn Tenno".equals(k)
                || "Breeders' Cup".equals(k)
                || "Mile Championship".equals(k);
    }

    public static synchronized void ensureTranslationFilesInitialized() {
        if (translationsInitialized && !dirtyNewDefaultKeys) {
            return;
        }
        translationsInitialized = true;
        dirtyNewDefaultKeys = false;
        try {
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create();

            String[] langs = {"en_us", "zh_tw", "zh_hk", "zh_cn", "ja_jp"};
            String readmeContent = "========================================================================\n"
                    + "  All-In-One Thoroughbred Mod — User-Editable Translation Files\n"
                    + "========================================================================\n"
                    + "You can freely edit any translation outside of items in this folder!\n\n"
                    + "Files in this folder:\n"
                    + "  - en_us.json : English UI, HUD, Stat Card & Tote Board strings\n"
                    + "  - zh_tw.json : Traditional Chinese (Taiwan) strings\n"
                    + "  - zh_hk.json : Traditional Chinese (Hong Kong) strings\n"
                    + "  - zh_cn.json : Simplified Chinese strings\n"
                    + "  - ja_jp.json : Japanese strings\n\n"
                    + "How to customize:\n"
                    + "  1. Open the .json file for your language in any text editor (e.g. Notepad / VS Code).\n"
                    + "  2. Edit the right-hand side value of any \"English Key\": \"Your Translation\" line.\n"
                    + "     (Keep any %s, %d, or %.1f placeholders intact if present.)\n"
                    + "  3. Save the file! The mod automatically detects changes within 2 seconds\n"
                    + "     while you play, or you can run /tb reloadlang in-game at any time!\n";

            java.util.Map<String, Long> newestLangMtime = new java.util.HashMap<>();
            for (java.io.File dir : getTranslationDirectories()) {
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                java.io.File readmeFile = new java.io.File(dir, "README_TRANSLATIONS.txt");
                if (!readmeFile.exists()) {
                    java.nio.file.Files.writeString(readmeFile.toPath(), readmeContent, java.nio.charset.StandardCharsets.UTF_8);
                }

                for (String lang : langs) {
                    java.io.File jsonFile = new java.io.File(dir, lang + ".json");
                    boolean fileExisted = jsonFile.exists() && jsonFile.length() > 2;
                    java.util.Map<String, String> activeMap = new java.util.LinkedHashMap<>();

                    int colIdx = switch (lang) {
                        case "zh_tw", "zh_hk" -> 1;
                        case "zh_cn" -> 2;
                        case "ja_jp" -> 3;
                        default -> 0;
                    };
                    boolean needsWriteBack = !fileExisted;

                    if (fileExisted) {
                        try {
                            String rawJson = java.nio.file.Files.readString(jsonFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(rawJson);
                            if (el != null && el.isJsonObject()) {
                                com.google.gson.JsonObject obj = el.getAsJsonObject();
                                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
                                    String k = entry.getKey();
                                    if (isRemovedLegacyTranslationKey(k)) {
                                        needsWriteBack = true;
                                        continue;
                                    }
                                    if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                                        String v = entry.getValue().getAsString();
                                        if (colIdx > 0 && isLikelyMojibake(v)) {
                                            String[] defArr = DEFAULT_TRANSLATIONS.get(k);
                                            if (defArr != null && colIdx < defArr.length && defArr[colIdx] != null) {
                                                v = defArr[colIdx];
                                                needsWriteBack = true;
                                            }
                                        }
                                        activeMap.put(k, v);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                            needsWriteBack = true;
                        }
                        // Backfill any newly added default keys (while respecting intentionally removed legacy keys)
                        for (java.util.Map.Entry<String, String[]> e : DEFAULT_TRANSLATIONS.entrySet()) {
                            String k = e.getKey();
                            if (!activeMap.containsKey(k) && !isRemovedLegacyTranslationKey(k)) {
                                String[] arr = e.getValue();
                                activeMap.put(k, (arr != null && colIdx < arr.length && arr[colIdx] != null) ? arr[colIdx] : k);
                                needsWriteBack = true;
                            }
                        }
                    } else {
                        for (java.util.Map.Entry<String, String[]> e : DEFAULT_TRANSLATIONS.entrySet()) {
                            String[] arr = e.getValue();
                            activeMap.put(e.getKey(), (arr != null && colIdx < arr.length && arr[colIdx] != null) ? arr[colIdx] : e.getKey());
                        }
                    }

                    if (needsWriteBack) {
                        com.google.gson.JsonObject outObj = new com.google.gson.JsonObject();
                        for (java.util.Map.Entry<String, String> me : activeMap.entrySet()) {
                            outObj.addProperty(me.getKey(), me.getValue());
                        }
                        java.nio.file.Files.writeString(jsonFile.toPath(), gson.toJson(outObj) + "\n", java.nio.charset.StandardCharsets.UTF_8);
                    }

                    long fileMtime = jsonFile.lastModified();
                    Long prevBest = newestLangMtime.get(lang);
                    if (prevBest == null || fileMtime >= prevBest) {
                        newestLangMtime.put(lang, fileMtime);
                        USER_TRANSLATION_OVERRIDES.put(lang, new java.util.concurrent.ConcurrentHashMap<>(activeMap));
                    }
                    TRANSLATION_FILE_MTIMES.put(jsonFile.getAbsolutePath(), fileMtime);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static synchronized int reloadExternalTranslations() {
        int loadedCount = 0;
        try {
            translationsInitialized = false;
            ensureTranslationFilesInitialized();
            String[] langs = {"en_us", "zh_tw", "zh_hk", "zh_cn", "ja_jp"};
            java.util.Map<String, Long> newestLangMtime = new java.util.HashMap<>();
            for (java.io.File dir : getTranslationDirectories()) {
                if (!dir.isDirectory()) continue;
                for (String lang : langs) {
                    java.io.File jsonFile = new java.io.File(dir, lang + ".json");
                    if (jsonFile.isFile() && jsonFile.length() > 2) {
                        try {
                            String rawJson = java.nio.file.Files.readString(jsonFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(rawJson);
                            if (el != null && el.isJsonObject()) {
                                com.google.gson.JsonObject obj = el.getAsJsonObject();
                                java.util.Map<String, String> map = new java.util.concurrent.ConcurrentHashMap<>();
                                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
                                    if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                                        map.put(entry.getKey(), entry.getValue().getAsString());
                                    }
                                }
                                long fileMtime = jsonFile.lastModified();
                                Long prevBest = newestLangMtime.get(lang);
                                if (prevBest == null || fileMtime >= prevBest) {
                                    newestLangMtime.put(lang, fileMtime);
                                    USER_TRANSLATION_OVERRIDES.put(lang, map);
                                }
                                loadedCount = Math.max(loadedCount, map.size());
                                TRANSLATION_FILE_MTIMES.put(jsonFile.getAbsolutePath(), fileMtime);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return loadedCount;
    }

    private static void maybeHotReloadTranslations() {
        long now = System.currentTimeMillis();
        if (now - lastHotReloadCheckMillis < 2000L) {
            return;
        }
        lastHotReloadCheckMillis = now;
        if (!translationsInitialized) {
            ensureTranslationFilesInitialized();
            return;
        }
        try {
            String langKey = resolveTranslationFileKey();
            for (java.io.File dir : getTranslationDirectories()) {
                java.io.File jsonFile = new java.io.File(dir, langKey + ".json");
                if (jsonFile.isFile()) {
                    long mtime = jsonFile.lastModified();
                    Long prev = TRANSLATION_FILE_MTIMES.get(jsonFile.getAbsolutePath());
                    if (prev == null || mtime > prev) {
                        reloadExternalTranslations();
                        return;
                    }
                }
            }
            if (dirtyNewDefaultKeys) {
                ensureTranslationFilesInitialized();
            }
        } catch (Throwable ignored) {
        }
    }

    private static String resolveTranslationFileKey() {
        String c = getActiveLangCode();
        if (c.startsWith("zh_hk") || c.startsWith("zh_mo")) return "zh_hk";
        if (isTraditionalChinese()) return "zh_tw";
        if (isSimplifiedChinese()) return "zh_cn";
        if (isJapanese()) return "ja_jp";
        return "en_us";
    }

        public static String trMood(String moodKey, String zhTw, String zhCn, String jaJp) {
        if (moodKey == null) return "";
        maybeHotReloadTranslations();
        String langKey = resolveTranslationFileKey();
        java.util.Map<String, String> overrides = USER_TRANSLATION_OVERRIDES.get(langKey);
        if (overrides != null) {
            String custom = overrides.get("Mood: " + moodKey);
            if (custom != null && !custom.isBlank()) return custom;
            custom = overrides.get("Mood." + moodKey);
            if (custom != null && !custom.isBlank()) return custom;
            custom = overrides.get(moodKey);
            if (custom != null && !custom.isBlank()) return custom;
        } else if ("zh_hk".equals(langKey) && USER_TRANSLATION_OVERRIDES.containsKey("zh_tw")) {
            java.util.Map<String, String> twOverrides = USER_TRANSLATION_OVERRIDES.get("zh_tw");
            String custom = twOverrides.get("Mood: " + moodKey);
            if (custom != null && !custom.isBlank()) return custom;
            custom = twOverrides.get("Mood." + moodKey);
            if (custom != null && !custom.isBlank()) return custom;
            custom = twOverrides.get(moodKey);
            if (custom != null && !custom.isBlank()) return custom;
        }
        return tr(moodKey, zhTw, zhCn, jaJp);
    }

    public static String trTrackCondition(String condKey, String zhTw, String zhCn, String jaJp) {
        if (condKey == null) return "";
        maybeHotReloadTranslations();
        String langKey = resolveTranslationFileKey();
        java.util.Map<String, String> overrides = USER_TRANSLATION_OVERRIDES.get(langKey);
        if (overrides != null) {
            String custom = overrides.get("Track: " + condKey);
            if (custom != null && !custom.isBlank()) return custom;
            custom = overrides.get("Track." + condKey);
            if (custom != null && !custom.isBlank()) return custom;
            custom = overrides.get("Track Condition: " + condKey);
            if (custom != null && !custom.isBlank()) return custom;
            custom = overrides.get(condKey);
            if (custom != null && !custom.isBlank()) return custom;
        } else if ("zh_hk".equals(langKey) && USER_TRANSLATION_OVERRIDES.containsKey("zh_tw")) {
            java.util.Map<String, String> twOverrides = USER_TRANSLATION_OVERRIDES.get("zh_tw");
            String custom = twOverrides.get("Track: " + condKey);
            if (custom != null && !custom.isBlank()) return custom;
            custom = twOverrides.get("Track." + condKey);
            if (custom != null && !custom.isBlank()) return custom;
            custom = twOverrides.get("Track Condition: " + condKey);
            if (custom != null && !custom.isBlank()) return custom;
            custom = twOverrides.get(condKey);
            if (custom != null && !custom.isBlank()) return custom;
        }
        return tr(condKey, zhTw, zhCn, jaJp);
    }

    public static String tr(String en, String zhTw, String zhCn, String jaJp) {
        if (en == null) return "";
        if (!DEFAULT_TRANSLATIONS.containsKey(en)) {
            synchronized (DEFAULT_TRANSLATIONS) {
                if (!DEFAULT_TRANSLATIONS.containsKey(en)) {
                    regDef(en, zhTw, zhCn, jaJp);
                    dirtyNewDefaultKeys = true;
                }
            }
        }
        maybeHotReloadTranslations();
        String langKey = resolveTranslationFileKey();
        java.util.Map<String, String> overrides = USER_TRANSLATION_OVERRIDES.get(langKey);
        if (overrides != null) {
            String custom = overrides.get(en);
            if (custom != null && !custom.isBlank()) {
                return custom;
            }
        } else if ("zh_hk".equals(langKey) && USER_TRANSLATION_OVERRIDES.containsKey("zh_tw")) {
            String twCustom = USER_TRANSLATION_OVERRIDES.get("zh_tw").get(en);
            if (twCustom != null && !twCustom.isBlank()) {
                return twCustom;
            }
        }
        if (isTraditionalChinese()) return zhTw != null ? zhTw : en;
        if (isSimplifiedChinese()) return zhCn != null ? zhCn : en;
        if (isJapanese()) return jaJp != null ? jaJp : en;
        return en;
    }

    /**
     * Translates HUD condition tags, active running strategy tags, and gate-break banners on the client
     * so they always display in the player's chosen language (and honor aiothoroughbred_translations/*.json)!
     */
    public static String localizeHudTag(String rawTag) {
        if (rawTag == null || rawTag.isEmpty()) return "";
        String out = rawTag;
        // Replace known strategy & condition bracket tags
        String[][] bracketTags = new String[][] {
                {"[BIG ESCAPE: PRESSURED]", "[大逃: 受迫]", "[大逃: 受迫]", "[大逃げ: 競り合い]"},
                {"[UNFADING ESCAPE!]", "[不滅大逃！]", "[不灭大逃！]", "[異次元の逃亡者！]"},
                {"[BIG ESCAPE SPURT]", "[大逃衝刺]", "[大逃冲刺]", "[大逃げスパート]"},
                {"[BIG ESCAPE]", "[大逃]", "[大逃]", "[大逃げ]"},
                {"[FRONT: CLEAR LEAD]", "[領放: 單騎領先]", "[领放: 单骑领先]", "[逃げ: 単騎先頭]"},
                {"[FRONT RUNNER]", "[領放]", "[领放]", "[逃げ]"},
                {"[FRONT SPURT]", "[領放衝刺]", "[领放冲刺]", "[逃げスパート]"},
                {"[PACE CHASER]", "[跟前]", "[跟前]", "[先行]"},
                {"[PACE SPURT]", "[跟前衝刺]", "[跟前冲刺]", "[先行抜け出し]"},
                {"[LATE SURGER]", "[居中]", "[居中]", "[差し]"},
                {"[LATE SURGE!]", "[居中突圍！]", "[居中突围！]", "[差し脚炸裂！]"},
                {"[END CLOSER SPURT!]", "[後追強襲！]", "[后追强袭！]", "[直線一気！]"},
                {"[END CLOSER]", "[後追]", "[后追]", "[追込]"},
                {"[RECKLESS OVERPACE!]", "[盲目搶放！]", "[盲目抢放！]", "[暴走オーバーペース！]"},
                {"[HELD BACK / KEEN]", "[強行留後 / 搶口]", "[强行留后 / 抢口]", "[折り合い欠く]"},
                {"[OUT OF RHYTHM]", "[節奏紊亂]", "[节奏紊乱]", "[ペース乱れ]"},
                {"[FIGHTING BIT!]", "[嚴重搶口！]", "[严重抢口！]", "[激しい掛かり！]"},
                {"[SEVERE OVERPACE!]", "[嚴重超速失速！]", "[严重超速失速！]", "[激しいオーバーペース！]"},
                {"[BOXED IN]", "[受困包圍]", "[受困包围]", "[馬群包囲・前壁]"},
                {"[DEATH'S DOOR]", "[瀕死邊緣]", "[濒死边缘]", "[危篤状態]"},
                {"[POOR PROGNOSIS]", "[預後不良]", "[预后不良]", "[予後不良]"},
                {"[A-FIB!]", "[心房顫動!]", "[心房颤动!]", "[心房細動!]"},
                {"[LARYNGEAL 2x STM]", "[喘鳴症 2倍耗耐]", "[喘鸣症 2倍耗耐]", "[喘鳴症 消費2倍]"},
                {"[LAME: 12 KM/H CAP]", "[跛行: 限速12km/h]", "[跛行: 限速12km/h]", "[跛行: 制限12km/h]"},
                {"[FEVER]", "[發燒]", "[发烧]", "[発熱]"},
                {"[BLEEDER/EIPH]", "[肺出血/EIPH]", "[肺出血/EIPH]", "[鼻出血/EIPH]"},
                {"[COLIC]", "[腹痛/絞痛]", "[腹痛/绞痛]", "[疝痛]"},
                {"[OVERWEIGHT]", "[體重過重]", "[体重过重]", "[太り気味]"},
                {"[UNSTABLE LEGS]", "[雙腿發軟]", "[双腿发软]", "[脚元不安]"},
                {"[RETIRED FROM INJURY]", "[因傷退役]", "[因伤退役]", "[怪我引退]"},
                {"[BROODMARE: PREGNANT]", "[繁殖牝馬: 懷孕中]", "[繁殖母马: 怀孕中]", "[繁殖牝馬: 受胎中]"},
                {"[BROODMARE: IN HEAT]", "[繁殖牝馬: 發情期]", "[繁殖母马: 发情期]", "[繁殖牝馬: 発情期]"},
                {"[RETIRED BROODMARE]", "[退役繁殖牝馬]", "[退役繁殖母马]", "[引退繁殖牝馬]"},
                {"[RETIRED STUD]", "[退役種馬]", "[退役种马]", "[引退種牡馬]"},
                {"PERFECT START!", "完美出閘！", "完美出闸！", "ロケットスタート！"},
                {"GOOD START!", "良好出閘！", "良好出闸！", "好スタート！"},
                {"LATE START!", "出閘遲緩！", "出闸迟缓！", "出遅れ！"},
                {"MISSED START! (>1.0s)", "嚴重漏閘！(>1.0秒)", "严重漏闸！(>1.0秒)", "大出遅れ！(>1.0秒)"}
        };
        for (String[] row : bracketTags) {
            if (out.contains(row[0])) {
                out = out.replace(row[0], tr(row[0], row[1], row[2], row[3]));
            }
        }
        return out;
    }

    /**
     * Translates composite GUI labels and phrases in ClientScreens (and checks aiothoroughbred_translations/*.json).
     */
    public static String applyUiTranslations(String text) {
        if (text == null || text.isEmpty()) return "";
        maybeHotReloadTranslations();
        String langKey = resolveTranslationFileKey();
        java.util.Map<String, String> overrides = USER_TRANSLATION_OVERRIDES.get(langKey);
        if (overrides != null) {
            String direct = overrides.get(text);
            if (direct != null && !direct.isBlank()) {
                return direct;
            }
        }
        if ("en_us".equals(langKey)) {
            return text;
        }
        String out = text;
        String[][] uiPhrases = new String[][] {
                {"=== Thoroughbred Registry Form ===", "=== 純血馬血統登記表 ===", "=== 纯血马血统登记表 ===", "=== サラブレッド血統登録書 ==="},
                {"1. Select Naming Language Rule:", "1. 選擇馬名語言規則:", "1. 选择马名语言规则:", "1. 馬名言語ルールを選択:"},
                {"2. Enter Unique Horse Name (Optional Crown Buttons Below):", "2. 輸入唯一馬名 (下方可點擊插入冠名):", "2. 输入唯一马名 (下方可点击插入冠名):", "2. 固有の馬名を入力 (下のボタンで冠名挿入可):"},
                {"Name fits naming rules", "✔ 馬名符合命名規則", "✔ 马名符合命名规则", "✔ 馬名ルール適合"},
                {"3. Select Gender:", "3. 選擇馬匹性別:", "3. 选择马匹性别:", "3. 性別を選択:"},
                {"Register Thoroughbred", "正式登記純血馬", "正式登记纯血马", "サラブレッド登録"},
                {"Insert Crown Name:", "插入馬主冠名:", "插入马主冠名:", "冠名を挿入:"},
                {"=== Horse Owner & Farm Registration (/tb owner) ===", "=== 馬主與牧場官方登記 (/tb owner) ===", "=== 马主与牧场官方登记 (/tb owner) ===", "=== 馬主・牧場公式登録 (/tb owner) ==="},
                {"=== Jockey Silk & Cap Creation Studio ===", "=== 騎師綵衣與帽飾設計工坊 ===", "=== 骑师彩衣与帽饰设计工坊 ===", "=== 勝負服＆ジョッキー帽作成スタジオ ==="},
                {"Customize Jersey Body, Sleeves & Jockey Cap (or edit PNG masks via 📁 Silk Folder):", "自訂綵衣主體、衣袖與騎師帽 (或點擊 📁 綵衣資料夾編輯 PNG 遮罩):", "自定义彩衣主体、衣袖与骑师帽 (或点击 📁 彩衣文件夹编辑 PNG 遮罩):", "胴・袖・帽子の配色と柄を設定 (📁フォルダからPNG直接編集も可):"},
                {"Live Cap & Silk", "綵衣與帽預覽", "彩衣与帽预览", "勝負服・帽プレビュー"},
                {"Body Color", "綵衣主色", "彩衣主色", "胴の基本色"},
                {"Body Pattern", "綵衣花紋", "彩衣花纹", "胴の模様"},
                {"Body Pat. Color", "綵衣花紋顏色", "彩衣花纹颜色", "胴の模様色"},
                {"Sleeve Color", "衣袖主色", "衣袖主色", "袖の基本色"},
                {"Sleeve Pattern", "衣袖花紋", "衣袖花纹", "袖の模様"},
                {"Sleeve Pat. Color", "衣袖花紋顏色", "衣袖花纹颜色", "袖の模様色"},
                {"Jockey Cap Color", "騎師帽主色", "骑师帽主色", "帽子の基本色"},
                {"Jockey Cap Pattern", "騎師帽花紋", "骑师帽花纹", "帽子の模様"},
                {"Cap Pat. Color", "騎師帽花紋顏色", "骑师帽花纹颜色", "帽子の模様色"},
                {"No registered Thoroughbreds match the current Crop / Filter.", "目前選擇的世代或篩選條件下沒有符合的純血馬。", "当前选择的世代或筛选条件下没有符合的纯血马。", "現在の世代・フィルターに一致する登録馬はいません。"},
                {"No official race finishes yet (Unraced Debut/Maiden).", "尚無正式出賽紀錄 (未出賽新馬)。", "尚无正式出赛记录 (未出赛新马)。", "公式レース出走履歴なし (未出走・新馬)。"},
                {"Hereditary Potential:", "遺傳潛力:", "遗传潜力:", "遺伝潜在能力:"},
                {"Life / Breeding:", "成長與繁殖狀態:", "成长与繁殖状态:", "成長・繁殖状態:"},
                {"Ailments:", "當前疾病:", "当前疾病:", "現在の疾病:"},
                {"Injury:", "當前傷患:", "当前伤患:", "現在の故障:"},
                {"Status:", "狀態:", "状态:", "状態:"},
                {"Traits:", "遺傳特性:", "遗传特性:", "遺伝特性:"},
                {"Cross:", "交配型態:", "交配形态:", "配合判定:"},
                {"Coat:", "毛色:", "毛色:", "毛色:"},
                {"Sire:", "父系 (Sire):", "父系 (Sire):", "父 (Sire):"},
                {"Dam:", "母系 (Dam):", "母系 (Dam):", "母 (Dam):"},
                {"Owner:", "馬主:", "马主:", "馬主:"},
                {"Temp:", "脾氣:", "脾气:", "気性:"},
                {"Pers:", "性格:", "性格:", "性格:"},
                {"Mood:", "心情:", "心情:", "やる気:"},
                {"Time:", "時間:", "时间:", "タイム:"},
                {"Margin:", "馬身差:", "马身差:", "着差:"},
                {"Purse:", "獎金:", "奖金:", "賞金:"},
                {"Bloodline:", "血統父母:", "血统父母:", "血統 (父母):"},
                {"(Click to view full Stat Card & Pedigree)", "(點擊查看完整能力卡與四代血統表)", "(点击查看完整能力卡与四代血统表)", "(クリックで詳細能力・4代血統表を表示)"},
                {"[LOCKED]", "[已鎖定]", "[已锁定]", "[ロック中]"},
                {"[Unclaimed]", "[無主馬]", "[无主马]", "[未所有]"},
                {"Unclaimed", "無主", "无主", "未所有"},
                {"[Deceased]", "[已故]", "[已故]", "[死去]"},
                {"Stallion / Colt", "雄馬 (牡)", "雄马 (牡)", "牡馬"},
                {"Mare / Filly", "雌馬 (牝)", "雌马 (牝)", "牝馬"},
                {"Gelding", "閹馬 (騙)", "阉马 (骗)", "セン馬"}
        };
        for (String[] row : uiPhrases) {
            if (out.contains(row[0])) {
                out = out.replace(row[0], tr(row[0], row[1], row[2], row[3]));
            }
        }
        return out;
    }

    public static String localizeStrategy(String strat) {
        if (strat == null || strat.isBlank()) return tr("Pace Chaser", "跟前", "跟前", "先行");
        return switch (strat.trim()) {
            case STRAT_BIG_ESCAPE -> tr("Big Escape", "大逃", "大逃", "大逃げ");
            case STRAT_BIG_ESCAPE_FRONT -> tr("Big Escape / Front", "大逃 / 領放", "大逃 / 领放", "大逃げ・逃げ");
            case STRAT_FRONT_RUNNER -> tr("Front Runner", "領放", "领放", "逃げ");
            case STRAT_FRONT_PACE -> tr("Front / Pace", "領放 / 跟前", "领放 / 跟前", "逃げ・先行");
            case STRAT_PACE_CHASER -> tr("Pace Chaser", "跟前", "跟前", "先行");
            case STRAT_PACE_LATE -> tr("Pace / Late", "跟前 / 居中", "跟前 / 居中", "先行・差し");
            case STRAT_LATE_SURGER -> tr("Late Surger", "居中", "居中", "差し");
            case STRAT_LATE_END -> tr("Late / End", "居中 / 後追", "居中 / 后追", "差し・追込");
            case STRAT_END_CLOSER -> tr("End Closer", "後追", "后追", "追込");
            case STRAT_TACTICAL -> tr("Tactical", "全能自在", "全能自在", "自在");
            default -> strat;
        };
    }

    public static String localizeGender(String gender) {
        if (gender == null || gender.isBlank()) return tr("Male", "雄馬 (牡)", "雄马 (牡)", "牡馬");
        if (GENDER_FEMALE.equalsIgnoreCase(gender)) return tr("Female", "雌馬 (牝)", "雌马 (牝)", "牝馬");
        if (GENDER_GELDING.equalsIgnoreCase(gender)) return tr("Gelding", "閹馬 (騙)", "阉马 (骗)", "セン馬");
        return tr("Male", "雄馬 (牡)", "雄马 (牡)", "牡馬");
    }

    public static String localizeTemperament(String temp) {
        if (temp == null || temp.isBlank()) return tr("Normal", "普通", "普通", "普通");
        if (TEMP_GENTLE.equalsIgnoreCase(temp)) return tr("Gentle", "溫馴", "温驯", "大人しい");
        if (TEMP_NORMAL.equalsIgnoreCase(temp)) return tr("Normal", "普通", "普通", "普通");
        if (TEMP_ROUGH.equalsIgnoreCase(temp)) return tr("Rough", "急躁", "急躁", "荒い");
        if (TEMP_TEMPERAMENTAL.equalsIgnoreCase(temp)) return tr("Temperamental", "暴躁", "暴躁", "気性難");
        if (TEMP_VOLATILE.equalsIgnoreCase(temp) || "Halo's Reincarnation".equalsIgnoreCase(temp)) return tr("Volatile", "狂暴", "狂暴", "超気性難");
        return temp;
    }

    public static String localizePersonality(String pers) {
        if (pers == null || pers.isBlank()) return tr("Competitive", "好勝", "好胜", "負けず嫌い");
        return switch (pers.trim()) {
            case PERS_COMPETITIVE -> tr("Competitive", "好勝", "好胜", "負けず嫌い");
            case PERS_LONE_WOLF -> tr("Lone Wolf", "孤高", "孤高", "一匹狼");
            case PERS_CROWD_PLEASER -> tr("Crowd Pleaser", "表演型", "表演型", "目立ちたがり");
            case PERS_LAID_BACK -> tr("Laid-Back", "悠閒", "悠闲", "マイペース");
            case PERS_GLUTTON -> tr("Glutton", "貪吃", "贪吃", "食いしん坊");
            case PERS_STOIC -> tr("Stoic", "堅毅", "坚毅", "ストイック");
            case PERS_CLINGY -> tr("Clingy", "黏人", "黏人", "甘えん坊");
            case PERS_TIMID -> tr("Timid", "膽怯", "胆怯", "臆病");
            case PERS_DEFEATIST -> tr("Defeatist", "氣餒", "气馁", "諦め癖");
            case PERS_QUIRKY -> tr("Quirky", "古怪", "古怪", "気分屋");
            default -> pers;
        };
    }

    public static String localizeAilment(String a) {
        if (a == null || a.isBlank()) return "";
        return switch (a.trim()) {
            case AILMENT_FEVER -> tr("Fever", "發燒", "发烧", "発熱");
            case AILMENT_LARYNGEAL -> tr("Laryngeal Hemiplegia", "喉頭偏癱 (喘鳴症)", "喉头偏瘫 (喘鸣症)", "喉頭片麻痺 (喘鳴症)");
            case AILMENT_AFIB -> tr("Atrial Fibrillation", "心房顫動", "心房颤动", "心房細動");
            case AILMENT_LAMENESS -> tr("Lameness", "跛行", "跛行", "跛行");
            case AILMENT_UNSTABLE_LEGS -> tr("Unstable Legs", "雙腿發軟", "双腿发软", "脚元不安");
            case AILMENT_LAMINITIS -> tr("Laminitis", "蹄葉炎", "蹄叶炎", "蹄葉炎");
            case AILMENT_POOR_PROGNOSIS -> tr("Poor Prognosis", "預後不良", "预后不良", "予後不良");
            case AILMENT_EIPH -> tr("EIPH (Bleeder)", "運動誘發肺出血", "运动诱发肺出血", "鼻出血 (EIPH)");
            case AILMENT_COLIC -> tr("Colic", "腹痛 (絞痛)", "腹痛 (绞痛)", "疝痛");
            case AILMENT_OVERWEIGHT -> tr("Overweight", "體重過重", "体重过重", "太り気味");
            default -> a;
        };
    }

    public static String localizeInjury(String inj) {
        if (inj == null || inj.isBlank()) return tr("None", "無", "无", "なし");
        return switch (inj.trim()) {
            case INJURY_HOOF_CRACK -> tr("Hoof Crack", "裂蹄", "裂蹄", "裂蹄");
            case INJURY_LIGAMENT_STRAIN -> tr("Suspensory Ligament Strain", "懸韌帶拉傷", "悬韧带拉伤", "繋靱帯炎");
            case INJURY_BOWED_TENDON -> tr("Bowed Tendon", "屈腱炎", "屈腱炎", "屈腱炎");
            case INJURY_NON_CATASTROPHIC_FRACTURE -> tr("Non-Catastrophic Fracture", "輕微骨折", "轻微骨折", "骨折 (治療可能)");
            case INJURY_CATASTROPHIC_FRACTURE -> tr("Catastrophic Fracture", "粉碎性骨折", "粉碎性骨折", "粉砕骨折");
            default -> inj;
        };
    }

    public static String localizeTrait(String tr) {
        if (tr == null || tr.isBlank()) return "";
        return switch (tr.trim()) {
            case TRAIT_SMALL_HOOVES -> tr("Small Hooves", "小蹄", "小蹄", "小さい蹄");
            case TRAIT_LARGE_HOOVES -> tr("Large Hooves", "大蹄", "大蹄", "大きい蹄");
            case TRAIT_IRON_CONSTITUTION -> tr("Iron Constitution", "鋼鐵體質", "钢铁体质", "頑丈な体質");
            case TRAIT_X_FACTOR -> tr("X-Factor", "X因子 (巨大心臟)", "X因子 (巨大心脏)", "Xファクター (強心臓)");
            case TRAIT_EXPLOSIVE_GEAR -> tr("Explosive Late Gear", "爆發末腳", "爆发末脚", "瞬発力");
            case TRAIT_BORN_MUDDER -> tr("Born Mudder", "爛地王", "烂地王", "道悪巧者");
            case TRAIT_HILL_CLIMBER -> tr("Hill Climber", "坡道專家", "坡道专家", "坂路巧者");
            case TRAIT_LIGHTNING_STARTER -> tr("Lightning Starter", "閃電出閘", "闪电出闸", "ロケットスタート");
            case TRAIT_CORNERING_MAESTRO -> tr("Cornering Maestro", "彎道大師", "弯道大师", "コーナー巧者");
            case TRAIT_DEEP_LUNG -> tr("Deep Lung Capacity", "超大肺活量", "超大肺活量", "驚異の肺活量");
            case TRAIT_SPRINTER_TWITCH -> tr("Sprinter's Fast-Twitch", "短途快肌", "短途快肌", "スプリンター筋繊維");
            case TRAIT_DIESEL_STAYER -> tr("Diesel Stayer", "長途耐力王", "长途耐力王", "ステイヤー心臓");
            case TRAIT_GLASS_LEGS -> tr("Glass Legs", "玻璃腿", "玻璃腿", "ガラスの脚");
            case TRAIT_ROARER_GENE -> tr("Roarer Gene", "喘鳴基因", "喘鸣基因", "喘鳴遺伝子");
            case TRAIT_WEAK_CONSTITUTION -> tr("Weak Constitution", "體弱多病", "体弱多病", "虚弱体質");
            case TRAIT_HIGH_STRUNG -> tr("High-Strung Nerves", "神經緊繃", "神经紧绷", "神経質");
            case TRAIT_SOFT_PALATE -> tr("Soft Palate Displacement", "軟顎移位", "软腭移位", "軟口蓋変位");
            case TRAIT_BRITTLE_HOOVES -> tr("Brittle Hooves", "脆弱馬蹄", "脆弱马蹄", "脆い蹄");
            default -> tr;
        };
    }

    // =========================================================================
    // RUNNING STRATEGY CONSTANTS (5-Position Spectrum + Dual-Adjacent + Tactical)
    // Position Spectrum Indices:
    //   0 = Big Escape (Oonige / 大逃げ) — Rare innate 5th strategy!
    //   1 = Front Runner (逃げ)
    //   2 = Pace Chaser (先行)
    //   3 = Late Surger (差し)
    //   4 = End Closer (追込)
    // =========================================================================
    public static final int POS_BIG_ESCAPE   = 0;
    public static final int POS_FRONT_RUNNER = 1;
    public static final int POS_PACE_CHASER  = 2;
    public static final int POS_LATE_SURGER  = 3;
    public static final int POS_END_CLOSER   = 4;

    public static final String STRAT_BIG_ESCAPE       = "Big Escape";
    public static final String STRAT_BIG_ESCAPE_FRONT = "Big Escape / Front";
    public static final String STRAT_FRONT_RUNNER     = "Front Runner";
    public static final String STRAT_FRONT_PACE       = "Front / Pace";
    public static final String STRAT_PACE_CHASER      = "Pace Chaser";
    public static final String STRAT_PACE_LATE        = "Pace / Late";
    public static final String STRAT_LATE_SURGER      = "Late Surger";
    public static final String STRAT_LATE_END         = "Late / End";
    public static final String STRAT_END_CLOSER       = "End Closer";
    public static final String STRAT_TACTICAL         = "Tactical";

    public static final String[] STRATEGIES = {
            STRAT_BIG_ESCAPE,
            STRAT_BIG_ESCAPE_FRONT,
            STRAT_FRONT_RUNNER,
            STRAT_FRONT_PACE,
            STRAT_PACE_CHASER,
            STRAT_PACE_LATE,
            STRAT_LATE_SURGER,
            STRAT_LATE_END,
            STRAT_END_CLOSER,
            STRAT_TACTICAL
    };

    public static final String TEMP_GENTLE = "Gentle";
    public static final String TEMP_NORMAL = "Normal";
    public static final String TEMP_ROUGH = "Rough";
    public static final String TEMP_TEMPERAMENTAL = "Temperamental";
    public static final String TEMP_VOLATILE = "Volatile";

    public static final String[] TEMPERAMENTS = {
            TEMP_GENTLE,
            TEMP_NORMAL,
            TEMP_ROUGH,
            TEMP_TEMPERAMENTAL,
            TEMP_VOLATILE
    };

    // Gender Constants (Male, Female, Gelding)
    public static final String GENDER_MALE    = "Male";
    public static final String GENDER_FEMALE  = "Female";
    public static final String GENDER_GELDING = "Gelding";

    // Step 19: 10 Distinct Horse Personalities (Separate from Temperament!)
    public static final String PERS_COMPETITIVE   = "Competitive";
    public static final String PERS_LONE_WOLF     = "Lone Wolf";
    public static final String PERS_CROWD_PLEASER = "Crowd Pleaser";
    public static final String PERS_LAID_BACK     = "Laid-Back";
    public static final String PERS_GLUTTON       = "Glutton";
    public static final String PERS_STOIC         = "Stoic";
    public static final String PERS_CLINGY        = "Clingy";
    public static final String PERS_TIMID         = "Timid";
    public static final String PERS_DEFEATIST     = "Defeatist";
    public static final String PERS_QUIRKY        = "Quirky";

    public static final String[] PERSONALITIES = {
            PERS_COMPETITIVE, PERS_LONE_WOLF, PERS_CROWD_PLEASER, PERS_LAID_BACK, PERS_GLUTTON,
            PERS_STOIC, PERS_CLINGY, PERS_TIMID, PERS_DEFEATIST, PERS_QUIRKY
    };

    // =========================================================================
    // STEP 17: AILMENTS & INJURIES CONSTANTS
    // =========================================================================
    public static final String AILMENT_FEVER           = "Fever";
    public static final String AILMENT_LARYNGEAL       = "Laryngeal Hemiplegia";
    public static final String AILMENT_AFIB            = "Atrial Fibrillation";
    public static final String AILMENT_LAMENESS        = "Lameness";
    public static final String AILMENT_UNSTABLE_LEGS   = "Unstable Legs";
    public static final String AILMENT_LAMINITIS       = "Laminitis";
    public static final String AILMENT_POOR_PROGNOSIS  = "Poor Prognosis";
    public static final String AILMENT_EIPH            = "EIPH (Bleeder)";
    public static final String AILMENT_COLIC           = "Colic";
    public static final String AILMENT_OVERWEIGHT      = "Overweight";

    public static final String[] ALL_AILMENTS = {
            AILMENT_FEVER,
            AILMENT_LARYNGEAL,
            AILMENT_AFIB,
            AILMENT_LAMENESS,
            AILMENT_UNSTABLE_LEGS,
            AILMENT_LAMINITIS,
            AILMENT_POOR_PROGNOSIS,
            AILMENT_EIPH,
            AILMENT_COLIC,
            AILMENT_OVERWEIGHT
    };

    public static final String INJURY_NONE                      = "";
    public static final String INJURY_HOOF_CRACK                = "Hoof Crack";
    public static final String INJURY_LIGAMENT_STRAIN           = "Suspensory Ligament Strain";
    public static final String INJURY_BOWED_TENDON              = "Bowed Tendon";
    public static final String INJURY_NON_CATASTROPHIC_FRACTURE = "Non-Catastrophic Fracture";
    public static final String INJURY_CATASTROPHIC_FRACTURE     = "Catastrophic Fracture";

    public static final String[] ALL_INJURIES = {
            INJURY_HOOF_CRACK,
            INJURY_LIGAMENT_STRAIN,
            INJURY_BOWED_TENDON,
            INJURY_NON_CATASTROPHIC_FRACTURE,
            INJURY_CATASTROPHIC_FRACTURE
    };

    // =========================================================================
    // STEP 18: BREEDING, 100 HP GENETICS, TRAITS & 4-GEN PEDIGREE CONSTANTS
    // =========================================================================
    public static final int PEDIGREE_SIZE = 30; // 2 Parents + 4 Grandparents + 8 Great-GP + 16 Great-Great-GP
    public static final int TOTAL_HP_SLOTS = 100;

    // Specialized Hoof Traits (Mutually Exclusive)
    public static final String TRAIT_SMALL_HOOVES      = "Small Hooves";
    public static final String TRAIT_LARGE_HOOVES      = "Large Hooves";

    // Positive & Tradeoff Genetic Traits
    public static final String TRAIT_IRON_CONSTITUTION = "Iron Constitution";
    public static final String TRAIT_X_FACTOR          = "X-Factor";
    public static final String TRAIT_EXPLOSIVE_GEAR    = "Explosive Late Gear";
    public static final String TRAIT_BORN_MUDDER       = "Born Mudder";
    public static final String TRAIT_HILL_CLIMBER      = "Hill Climber";
    public static final String TRAIT_LIGHTNING_STARTER = "Lightning Starter";
    public static final String TRAIT_CORNERING_MAESTRO = "Cornering Maestro";
    public static final String TRAIT_DEEP_LUNG         = "Deep Lung Capacity";
    public static final String TRAIT_SPRINTER_TWITCH   = "Sprinter's Fast-Twitch";
    public static final String TRAIT_DIESEL_STAYER     = "Diesel Stayer";

    // Negative / Inbreeding Depression Genetic Traits
    public static final String TRAIT_GLASS_LEGS        = "Glass Legs";
    public static final String TRAIT_ROARER_GENE       = "Roarer Gene";
    public static final String TRAIT_WEAK_CONSTITUTION = "Weak Constitution";
    public static final String TRAIT_HIGH_STRUNG       = "High-Strung Nerves";
    public static final String TRAIT_SOFT_PALATE       = "Soft Palate Displacement";
    public static final String TRAIT_BRITTLE_HOOVES    = "Brittle Hooves";

    public static final String[] ALL_GENETIC_TRAITS = {
            TRAIT_SMALL_HOOVES,
            TRAIT_LARGE_HOOVES,
            TRAIT_IRON_CONSTITUTION,
            TRAIT_X_FACTOR,
            TRAIT_EXPLOSIVE_GEAR,
            TRAIT_BORN_MUDDER,
            TRAIT_HILL_CLIMBER,
            TRAIT_LIGHTNING_STARTER,
            TRAIT_CORNERING_MAESTRO,
            TRAIT_DEEP_LUNG,
            TRAIT_SPRINTER_TWITCH,
            TRAIT_DIESEL_STAYER,
            TRAIT_GLASS_LEGS,
            TRAIT_ROARER_GENE,
            TRAIT_WEAK_CONSTITUTION,
            TRAIT_HIGH_STRUNG,
            TRAIT_SOFT_PALATE,
            TRAIT_BRITTLE_HOOVES
    };

    /**
     * Stores a single race finish in a horse's career history.
     * Pre-edited race metadata (raceName, grade, trackSummary) is preserved as a historical memo
     * even if the race is later edited or switched to another Base Type on the Racing Console.
     * If the race is deleted from the Racing Console, records matching raceId (or raceName) are erased.
     */
    public static class CareerRaceRecord {
        public String raceId = "";
        public String raceName = "";
        public String grade = "Open";
        public String trackSummary = "";
        public int placement = 1;
        public int totalRunners = 1;
        public String finishTime = "00:00.00";
        public String margin = "-";
        public long earnings = 0L;

        public CareerRaceRecord() {
        }

        public CareerRaceRecord(String raceId, String raceName, String grade, String trackSummary,
                                int placement, int totalRunners, String finishTime,
                                String margin, long earnings) {
            this.raceId = raceId != null ? raceId : "";
            this.raceName = raceName != null ? raceName : "Official Race";
            this.grade = grade != null ? grade : "Open";
            this.trackSummary = trackSummary != null ? trackSummary : "";
            this.placement = Math.max(1, placement);
            this.totalRunners = Math.max(1, totalRunners);
            this.finishTime = finishTime != null ? finishTime : "00:00.00";
            this.margin = margin != null ? margin : "-";
            this.earnings = Math.max(0L, earnings);
        }

        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("RaceId", raceId != null ? raceId : "");
            t.putString("RaceName", raceName);
            t.putString("Grade", grade);
            t.putString("TrackSummary", trackSummary);
            t.putInt("Placement", placement);
            t.putInt("TotalRunners", totalRunners);
            t.putString("FinishTime", finishTime);
            t.putString("Margin", margin);
            t.putLong("Earnings", earnings);
            return t;
        }

        public static CareerRaceRecord fromNBT(CompoundTag t) {
            return new CareerRaceRecord(
                    t.contains("RaceId") ? t.getString("RaceId") : "",
                    t.getString("RaceName"),
                    t.getString("Grade"),
                    t.getString("TrackSummary"),
                    t.getInt("Placement"),
                    t.getInt("TotalRunners"),
                    t.getString("FinishTime"),
                    t.getString("Margin"),
                    t.getLong("Earnings")
            );
        }

        public String getFormattedGradeBadge() {
            String g = grade != null ? grade.trim() : "Open";
            if ("G1".equalsIgnoreCase(g)) return "§b§l[G1]";
            if ("G2".equalsIgnoreCase(g)) return "§d§l[G2]";
            if ("G3".equalsIgnoreCase(g)) return "§a§l[G3]";
            if ("Listed".equalsIgnoreCase(g) || "L".equalsIgnoreCase(g)) return "§e§l[Listed]";
            if ("Open".equalsIgnoreCase(g) || "OP".equalsIgnoreCase(g)) return "§6§l[OP]";
            if ("Pre-Open".equalsIgnoreCase(g) || "Pre-OP".equalsIgnoreCase(g)) return "§e[Pre-OP]";
            if (g.toLowerCase(Locale.ROOT).contains("debut") || g.toLowerCase(Locale.ROOT).contains("maiden")) {
                return "§f[Debut]";
            }
            return "§7[" + g + "]";
        }

        public String getFormattedPlacement() {
            if (placement >= 100 || "DSQ".equalsIgnoreCase(finishTime)) return "§4§lDSQ";
            if (placement == 99 || "DNF".equalsIgnoreCase(finishTime)) return "§c§lDNF";
            if (placement == 1) return "§6§l1st";
            if (placement == 2) return "§b§l2nd";
            if (placement == 3) return "§c§l3rd";
            return "§7" + placement + getOrdinalSuffix(placement);
        }

        public int getPlacementAccentColor() {
            if (placement >= 100 || "DSQ".equalsIgnoreCase(finishTime)) return 0xFF881111; // Dark Crimson for DSQ
            if (placement == 99 || "DNF".equalsIgnoreCase(finishTime)) return 0xFFAA2222;  // Red for DNF
            if (placement == 1) return 0xFFD4AF37; // Gold
            if (placement == 2) return 0xFF55CCFF; // Silver/Cyan
            if (placement == 3) return 0xFFDD8844; // Bronze
            if (placement <= 5) return 0xFF55FF55; // Placed 4th-5th (Green)
            return 0xFF445566;                     // Slate
        }

        private static String getOrdinalSuffix(int n) {
            int mod100 = n % 100;
            if (mod100 >= 11 && mod100 <= 13) return "th";
            return switch (n % 10) {
                case 1 -> "st";
                case 2 -> "nd";
                case 3 -> "rd";
                default -> "th";
            };
        }
    }

    private boolean registered = false;
    private String name = "";
    private String gender = "Male";
    private String strategy = STRAT_PACE_CHASER;
    private String temperament = TEMP_NORMAL;
    private String personality = PERS_COMPETITIVE;
    private boolean competitiveLoserBoost = false;
    private int overfedStreak = 0;
    private double overweightExerciseMeters = 0.0;
    private long lastOwnerInteractionMcDay = -1L;

    // Ownership (/tb claim), Lock (/tb lock), Stable (/tb setstable) & Race Failsafe
    private String ownerUUID = "";
    private String ownerName = "";
    private String ownerSilkCode = "";
    private String cropTag = "";
    private boolean locked = false;
    private boolean hasStable = false;
    private double stableX = 0.0;
    private double stableY = 0.0;
    private double stableZ = 0.0;
    private String stableDimension = "minecraft:overworld";
    private boolean inRace = false;
    private boolean wearingRaceSilk = false;
    private boolean deathsDoor = false;
    private int baseAdultEquineAgeYears = 2;
    private long adultStartTimestampMillis = 0L;
    private int cachedEquineAgeYears = 2;

    // Core stats stored on internal 0-1000 scale (displayed as 0.0-100.0)
    private int speedCurrent = 200;
    private int speedMax = 700;

    private int staminaCurrent = 200;
    private int staminaMax = 700;

    private int powerCurrent = 200;
    private int powerMax = 700;

    private int gutsCurrent = 200;
    private int gutsMax = 700;

    private int witsCurrent = 200;
    private int witsMax = 700;

    // Training EXP accumulators toward the next +1 internal stat point (+0.1 display)
    private double speedExp = 0.0;
    private double staminaExp = 0.0;
    private double powerExp = 0.0;
    private double gutsExp = 0.0;
    private double witsExp = 0.0;

    // Care, Conditioning, Bond & Mood (0.0 to 100.0 scale)
    private double hunger = 100.0;
    private double thirst = 100.0;
    private double fatigue = 0.0;
    private double bond = 20.0;
    private double mood = 55.0; // Starts in Normal (41-60)

    // Enchanted Golden Apple Blessing (1 real-life day = 86,400,000 milliseconds)
    private long enchantedAppleExpiryMillis = 0L;

    // Optional Training Gear toggles
    private boolean weightedTack = false;
    private boolean blinkersHood = false;

    // Track aptitudes (0-10, 8 is baseline)
    private int turfAptitude = 8;
    private int dirtAptitude = 5;

    // Distance aptitudes in meters (multiples of 100, e.g. 1200 to 1800)
    private int minDistance = 1200;
    private int maxDistance = 1800;

    // Career Finishes & Total Prize Earnings
    private final List<CareerRaceRecord> careerHistory = new ArrayList<>();
    private long totalEarnings = 0L;

    // Step 17: Ailments, Injuries, Poor Prognosis & Career-Ending Speed Lock
    private final List<String> activeAilments = new ArrayList<>();
    private String activeInjury = INJURY_NONE;
    private double injuryDaysRemaining = 0.0;
    private boolean vetTreatedThisInjury = false;
    private boolean poorPrognosis = false;
    private boolean poorPrognosisAppleImmunity = false;
    private int poorPrognosisGoldenApplesUsed = 0;
    private boolean poorPrognosisGoldenAppleActive = false;
    private int poorPrognosisGoldenAppleDeathChance = 49;
    private boolean triedToSaveWithApples = false;
    private boolean careerEnded = false;
    private long lastCheckedMinecraftDay = -1L;
    private long lastCheckedDeathsDoorRealDay = -1L;

    // Step 18: Retirement, Mare Heat, Pregnancy, Foal Growth, 100 HP Genetics, Traits, 4-Gen Pedigree & Pure White Coat
    private boolean retired = false;
    private boolean inHeat = false;
    private boolean manualHeatOverride = false;
    private boolean pregnant = false;
    private long pregnancyConceptionMillis = 0L;
    private long pregnancyDurationMillis = 0L;
    private String pregnantSireName = "";
    private String pregnantSireUUID = "";
    private CompoundTag pregnantSireNbt = new CompoundTag();

    private boolean foal = false;
    private boolean yearling = false;
    private boolean bredFoalPendingName = false;
    private long birthTimestampMillis = 0L;
    private long foalGrowDurationMillis = 0L;
    private long yearlingStartMillis = 0L;
    private long yearlingGrowDurationMillis = 0L;

    private int hereditaryPoints = 68;
    private final byte[] geneSlots = new byte[TOTAL_HP_SLOTS];
    private boolean atavismTriggered = false;
    private String inbreedingSummary = "Outcross";
    private boolean inbredDepressionTriggered = false;
    private final List<String> geneticTraits = new ArrayList<>();
    private int xFactorCopies = 0; // Hidden X-Factor gene copies (0 = non-carrier, 1 = 1 X+ chromosome, 2 = 2 X+ chromosomes on filly/mare)
    private final String[] pedigreeTree = new String[PEDIGREE_SIZE];
    private final int[] pedigreeHP = new int[PEDIGREE_SIZE];
    private boolean pureWhiteCoat = false;

    public ThoroughbredData() {
        for (int i = 0; i < PEDIGREE_SIZE; i++) {
            this.pedigreeTree[i] = "";
            this.pedigreeHP[i] = 65;
        }
        initGeneSlotsFromHP(this.hereditaryPoints, RandomSource.create());
    }

    public static boolean isThoroughbred(Horse horse) {
        if (horse == null) {
            return false;
        }
        CompoundTag root = horse.getPersistentData();
        if (!root.contains(NBT_KEY)) {
            return false;
        }
        return root.getCompound(NBT_KEY).getBoolean("Registered");
    }

    public static ThoroughbredData fromHorse(Horse horse) {
        ThoroughbredData data = new ThoroughbredData();
        if (horse != null && horse.getPersistentData().contains(NBT_KEY)) {
            data.loadFromNBT(horse.getPersistentData().getCompound(NBT_KEY));
        }
        return data;
    }

    public void saveToHorse(Horse horse) {
        if (horse != null) {
            enforceHealthMoodAndFatigueRules();
            if (!horse.level().isClientSide && horse.level().getServer() != null) {
                StudbookSavedData studbook = StudbookSavedData.get(horse.level().getServer().overworld());
                if (this.cropTag == null || this.cropTag.isBlank()) {
                    this.cropTag = studbook.computeCurrentCropTag();
                }
                getEquineAgeYears(studbook);
                if (hasOwner()) {
                    try {
                        UUID u = UUID.fromString(this.ownerUUID);
                        String formatted = studbook.getFormattedOwnerName(u, this.ownerName);
                        if (formatted != null && !formatted.isBlank()) {
                            this.ownerName = formatted;
                        }
                        String silk = studbook.getOwnerSilkCompactCode(u);
                        if (silk != null && !silk.isBlank()) {
                            this.ownerSilkCode = silk;
                        }
                    } catch (Exception ignored) {
                    }
                } else {
                    this.ownerSilkCode = "";
                }
                horse.getPersistentData().put(NBT_KEY, this.toNBT());
                studbook.updateHorseRecord(horse, this);
                ModNetwork.broadcastHorseCondition(horse, this);
            } else {
                horse.getPersistentData().put(NBT_KEY, this.toNBT());
            }
        }
    }

    public void generateRandomGenetics(RandomSource random, String chosenName, String chosenGender) {
        this.registered = true;
        this.name = chosenName;
        this.gender = chosenGender;
        this.baseAdultEquineAgeYears = 2;
        this.adultStartTimestampMillis = System.currentTimeMillis();
        this.cachedEquineAgeYears = 2;
        this.deathsDoor = false;

        int roll = random.nextInt(100);
        int staminaBias = 0;

        if (roll < 20) {
            this.minDistance = 1000;
            this.maxDistance = 1200;
            staminaBias = -180;
        } else if (roll < 45) {
            this.minDistance = 1000 + random.nextInt(3) * 100;
            this.maxDistance = 1600 + random.nextInt(3) * 100;
            staminaBias = -90;
        } else if (roll < 75) {
            this.minDistance = 1600 + random.nextInt(5) * 100;
            this.maxDistance = 2200 + random.nextInt(5) * 100;
            staminaBias = 20;
        } else if (roll < 90) {
            this.minDistance = 2000 + random.nextInt(6) * 100;
            this.maxDistance = 3000 + random.nextInt(7) * 100;
            staminaBias = 140;
        } else if (roll < 96) {
            this.minDistance = 2800 + random.nextInt(5) * 100;
            this.maxDistance = 4100 + random.nextInt(4) * 100;
            staminaBias = 220;
        } else {
            this.minDistance = 1200;
            this.maxDistance = 3200;
            staminaBias = 80;
        }

        this.speedMax = clampStat(520 + random.nextInt(481) - (staminaBias / 3));
        this.staminaMax = clampStat(520 + random.nextInt(401) + staminaBias);
        this.powerMax = clampStat(500 + random.nextInt(501));
        this.gutsMax = clampStat(500 + random.nextInt(501));
        this.witsMax = clampStat(500 + random.nextInt(501));

        this.speedCurrent = clampStat((int) (this.speedMax * (0.35 + random.nextDouble() * 0.20)));
        this.staminaCurrent = clampStat((int) (this.staminaMax * (0.35 + random.nextDouble() * 0.20)));
        this.powerCurrent = clampStat((int) (this.powerMax * (0.35 + random.nextDouble() * 0.20)));
        this.gutsCurrent = clampStat((int) (this.gutsMax * (0.35 + random.nextDouble() * 0.20)));
        this.witsCurrent = clampStat((int) (this.witsMax * (0.35 + random.nextDouble() * 0.20)));

        this.hunger = 100.0;
        this.thirst = 100.0;
        this.fatigue = 0.0;
        this.bond = 20.0;
        this.mood = 55.0; // Normal mood on registration
        this.enchantedAppleExpiryMillis = 0L;
        this.careerHistory.clear();
        this.totalEarnings = 0L;

        if (random.nextBoolean()) {
            this.turfAptitude = 8 + random.nextInt(3);
            this.dirtAptitude = 1 + random.nextInt(8);
        } else {
            this.dirtAptitude = 8 + random.nextInt(3);
            this.turfAptitude = 1 + random.nextInt(8);
        }

        // Running Strategy Roll:
        // - 2%: Pure Big Escape (Oonige, e.g. Twin Turbo)
        // - 2%: Dual Big Escape / Front (e.g. Silence Suzuka)
        // - 52% (13% each): Single Specialist (Front Runner, Pace Chaser, Late Surger, End Closer)
        // - 36% (12% each): Dual Adjacent Runner (Front / Pace, Pace / Late, Late / End)
        // - 8%: Tactical All-Rounder (Front, Pace, Late, End)
        int stratRoll = random.nextInt(100);
        if (stratRoll < 2) {
            this.strategy = STRAT_BIG_ESCAPE;
        } else if (stratRoll < 4) {
            this.strategy = STRAT_BIG_ESCAPE_FRONT;
        } else if (stratRoll < 17) {
            this.strategy = STRAT_FRONT_RUNNER;
        } else if (stratRoll < 29) {
            this.strategy = STRAT_FRONT_PACE;
        } else if (stratRoll < 42) {
            this.strategy = STRAT_PACE_CHASER;
        } else if (stratRoll < 54) {
            this.strategy = STRAT_PACE_LATE;
        } else if (stratRoll < 67) {
            this.strategy = STRAT_LATE_SURGER;
        } else if (stratRoll < 79) {
            this.strategy = STRAT_LATE_END;
        } else if (stratRoll < 92) {
            this.strategy = STRAT_END_CLOSER;
        } else {
            this.strategy = STRAT_TACTICAL;
        }

        int tempRoll = random.nextInt(100);
        if (tempRoll < 25) {
            this.temperament = TEMP_GENTLE;
        } else if (tempRoll < 65) {
            this.temperament = TEMP_NORMAL;
        } else if (tempRoll < 87) {
            this.temperament = TEMP_ROUGH;
        } else if (tempRoll < 98) {
            this.temperament = TEMP_TEMPERAMENTAL;
        } else {
            this.temperament = TEMP_VOLATILE;
        }

        // Roll Personality (10 distinct personalities)
        this.personality = PERSONALITIES[random.nextInt(PERSONALITIES.length)];
        this.competitiveLoserBoost = false;
        this.overfedStreak = 0;
        this.overweightExerciseMeters = 0.0;

        // Step 18: Initialize Foundation Pedigree, 100 Hereditary Points (HP), Genetic Traits & Pure-White Mutation
        for (int i = 0; i < PEDIGREE_SIZE; i++) {
            this.pedigreeTree[i] = "";
            this.pedigreeHP[i] = 60 + random.nextInt(21);
        }
        this.geneticTraits.clear();
        this.inbreedingSummary = "Foundation (Outcross)";
        this.atavismTriggered = false;
        this.inbredDepressionTriggered = false;

        // Roll for innate Hoof Trait on foundation horses (8% Small Hooves, 8% Large Hooves)
        int hoofRoll = random.nextInt(100);
        if (hoofRoll < 8) {
            applyTraitAtBirth(TRAIT_SMALL_HOOVES);
        } else if (hoofRoll < 16) {
            applyTraitAtBirth(TRAIT_LARGE_HOOVES);
        }

        // Small chance for one additional innate trait on foundation horses
        this.xFactorCopies = 0;
        int extraTraitRoll = random.nextInt(100);
        if (extraTraitRoll < 5) {
            this.geneticTraits.add(TRAIT_IRON_CONSTITUTION);
        } else if (extraTraitRoll < 9) {
            // Foundation X-Factor roll (4% chance to carry an X+ chromosome):
            // Stallions (X+Y): 50% active X-Factor expression, 50% hidden carrier (passes X+ to 100% of daughters)
            // Mares (X+X): 20% active X-Factor expression, 80% hidden carrier (passes X+ to 50% of sons & daughters)
            this.xFactorCopies = 1;
            boolean isFemale = "Female".equalsIgnoreCase(this.gender);
            int expressChance = isFemale ? 20 : 50;
            if (random.nextInt(100) < expressChance) {
                this.geneticTraits.add(TRAIT_X_FACTOR);
            }
        } else if (extraTraitRoll < 13) {
            this.geneticTraits.add(TRAIT_EXPLOSIVE_GEAR);
        } else if (extraTraitRoll < 17) {
            this.geneticTraits.add(TRAIT_BORN_MUDDER);
        } else if (extraTraitRoll < 21) {
            this.geneticTraits.add(TRAIT_HILL_CLIMBER);
        } else if (extraTraitRoll < 25) {
            this.geneticTraits.add(TRAIT_LIGHTNING_STARTER);
        } else if (extraTraitRoll < 29) {
            this.geneticTraits.add(TRAIT_CORNERING_MAESTRO);
        } else if (extraTraitRoll < 33) {
            this.geneticTraits.add(TRAIT_DEEP_LUNG);
        } else if (extraTraitRoll < 36) {
            this.geneticTraits.add(TRAIT_SPRINTER_TWITCH);
        } else if (extraTraitRoll < 39) {
            this.geneticTraits.add(TRAIT_DIESEL_STAYER);
        } else if (extraTraitRoll < 41) {
            this.geneticTraits.add(TRAIT_GLASS_LEGS);
        } else if (extraTraitRoll < 43) {
            this.geneticTraits.add(TRAIT_SOFT_PALATE);
        } else if (extraTraitRoll < 45) {
            this.geneticTraits.add(TRAIT_BRITTLE_HOOVES);
        }

        // Calculate Foundation Hereditary Points (HP) from generated max stats (0-100 scale)
        int avgMax = (Math.min(1000, this.speedMax) + Math.min(1000, this.staminaMax)
                + Math.min(1000, this.powerMax) + Math.min(1000, this.gutsMax) + Math.min(1000, this.witsMax)) / 5;
        this.hereditaryPoints = Mth.clamp(Math.round(avgMax / 10.0F) + (random.nextInt(7) - 3), 45, 92);
        initGeneSlotsFromHP(this.hereditaryPoints, random);

        // 1% rare de novo Pure White coat mutation on foundation Thoroughbreds
        this.pureWhiteCoat = (random.nextInt(100) == 0);
    }

    // =========================================================================
    // RUNNING STRATEGY HELPERS (5-Position Spectrum & Compatibility)
    // =========================================================================
    /**
     * Returns true if this horse's innate strategy includes the given position index (0..4):
     *   0 = Big Escape (Oonige) — ONLY true for innate "Big Escape" or "Big Escape / Front" horses!
     *   1 = Front Runner
     *   2 = Pace Chaser
     *   3 = Late Surger
     *   4 = End Closer
     */
    public boolean canUseStrategyIndex(int posIndex) {
        String s = this.strategy != null ? this.strategy.trim() : STRAT_PACE_CHASER;
        if (STRAT_BIG_ESCAPE.equalsIgnoreCase(s) || "Oonige".equalsIgnoreCase(s)) {
            return posIndex == POS_BIG_ESCAPE;
        }
        if (STRAT_BIG_ESCAPE_FRONT.equalsIgnoreCase(s)) {
            return posIndex == POS_BIG_ESCAPE || posIndex == POS_FRONT_RUNNER;
        }
        if (STRAT_FRONT_RUNNER.equalsIgnoreCase(s)) {
            return posIndex == POS_FRONT_RUNNER;
        }
        if (STRAT_FRONT_PACE.equalsIgnoreCase(s)) {
            return posIndex == POS_FRONT_RUNNER || posIndex == POS_PACE_CHASER;
        }
        if (STRAT_PACE_CHASER.equalsIgnoreCase(s)) {
            return posIndex == POS_PACE_CHASER;
        }
        if (STRAT_PACE_LATE.equalsIgnoreCase(s)) {
            return posIndex == POS_PACE_CHASER || posIndex == POS_LATE_SURGER;
        }
        if (STRAT_LATE_SURGER.equalsIgnoreCase(s)) {
            return posIndex == POS_LATE_SURGER;
        }
        if (STRAT_LATE_END.equalsIgnoreCase(s)) {
            return posIndex == POS_LATE_SURGER || posIndex == POS_END_CLOSER;
        }
        if (STRAT_END_CLOSER.equalsIgnoreCase(s)) {
            return posIndex == POS_END_CLOSER;
        }
        if (STRAT_TACTICAL.equalsIgnoreCase(s)) {
            // Tactical masters all 4 standard strategies (Front, Pace, Late, End), but NOT Big Escape (Oonige)!
            return posIndex >= POS_FRONT_RUNNER && posIndex <= POS_END_CLOSER;
        }
        return posIndex == POS_PACE_CHASER;
    }

    public boolean isInnateBigEscapeRunner() {
        return canUseStrategyIndex(POS_BIG_ESCAPE);
    }

    /**
     * Returns how many steps away (0, 1, 2, 3, or 4) the active running position (0..4) is
     * from this horse's closest preferred running strategy.
     */
    public int getStrategyStepsOff(int activePosIndex) {
        int clamped = Mth.clamp(activePosIndex, POS_BIG_ESCAPE, POS_END_CLOSER);
        if (canUseStrategyIndex(clamped)) {
            return 0;
        }
        int minSteps = 4;
        for (int i = POS_BIG_ESCAPE; i <= POS_END_CLOSER; i++) {
            if (canUseStrategyIndex(i)) {
                minSteps = Math.min(minSteps, Math.abs(clamped - i));
            }
        }
        return minSteps;
    }

    /**
     * Returns true if the horse's active position is further BACK in the pack than all of its preferred positions
     * (e.g., a Front Runner or Big Escape runner being held back in the pack -> fights the reins / keen!).
     */
    public boolean isHeldTooFarBack(int activePosIndex) {
        int maxPreferred = POS_BIG_ESCAPE;
        for (int i = POS_BIG_ESCAPE; i <= POS_END_CLOSER; i++) {
            if (canUseStrategyIndex(i)) {
                maxPreferred = Math.max(maxPreferred, i);
            }
        }
        return activePosIndex > maxPreferred;
    }

    public String getFormattedStrategy() {
        String s = this.strategy != null ? this.strategy.trim() : STRAT_PACE_CHASER;
        String loc = localizeStrategy(s);
        if (STRAT_BIG_ESCAPE.equalsIgnoreCase(s)) return "§c§l" + loc;
        if (STRAT_BIG_ESCAPE_FRONT.equalsIgnoreCase(s)) return "§6§l" + loc;
        if (STRAT_TACTICAL.equalsIgnoreCase(s)) return "§d§l" + loc;
        if (s.contains("/")) return "§b" + loc;
        return "§f" + loc;
    }

    /**
     * Visual 5-Slot Strategy Bar for the Thoroughbred Stat Card:
     * Shows [BIG ESCAPE] and the 4 standard positions [FRONT | PACE | LATE | END].
     */
    public String getStrategyAptitudeBar() {
        StringBuilder sb = new StringBuilder();
        sb.append(tr("ESCAPE", "大逃", "大逃", "大逃")).append(" [");
        sb.append(canUseStrategyIndex(POS_BIG_ESCAPE) ? "§c■§a" : "=");
        sb.append("]  ").append(tr("FRONT", "領放", "领放", "逃げ")).append(" [");
        for (int i = POS_FRONT_RUNNER; i <= POS_END_CLOSER; i++) {
            if (canUseStrategyIndex(i)) {
                sb.append("§e■§a");
            } else {
                sb.append('=');
            }
        }
        sb.append("] ").append(tr("CLOSER", "後追", "后追", "追込")).append(" (").append(localizeStrategy(this.strategy)).append(")");
        return sb.toString();
    }

    public static String getPositionStrategyName(int posIndex) {
        return switch (posIndex) {
            case POS_BIG_ESCAPE -> localizeStrategy(STRAT_BIG_ESCAPE);
            case POS_FRONT_RUNNER -> localizeStrategy(STRAT_FRONT_RUNNER);
            case POS_PACE_CHASER -> localizeStrategy(STRAT_PACE_CHASER);
            case POS_LATE_SURGER -> localizeStrategy(STRAT_LATE_SURGER);
            case POS_END_CLOSER -> localizeStrategy(STRAT_END_CLOSER);
            default -> localizeStrategy(STRAT_PACE_CHASER);
        };
    }

    public static String rollFoalTemperament(RandomSource random, String sireTemp, String damTemp) {
        int sireIdx = getTemperamentIndex(sireTemp);
        int damIdx = getTemperamentIndex(damTemp);

        int volatileChance = 2;
        if (sireIdx == 3) volatileChance += 3;
        else if (sireIdx == 4) volatileChance += 12;
        if (damIdx == 3) volatileChance += 3;
        else if (damIdx == 4) volatileChance += 12;

        if (random.nextInt(100) < volatileChance) {
            return TEMP_VOLATILE;
        }

        double parentAvg = (Math.min(3, sireIdx) + Math.min(3, damIdx)) / 2.0;
        if (random.nextDouble() < 0.60) {
            int offset = random.nextInt(3) - 1;
            int chosen = Mth.clamp((int) Math.round(parentAvg) + offset, 0, 3);
            return TEMPERAMENTS[chosen];
        } else {
            int r = random.nextInt(100);
            if (r < 30) return TEMP_GENTLE;
            if (r < 65) return TEMP_NORMAL;
            if (r < 85) return TEMP_ROUGH;
            return TEMP_TEMPERAMENTAL;
        }
    }

    public static String rollFoalPersonality(RandomSource random, String sirePers, String damPers) {
        int r = random.nextInt(100);
        if (r < 32 && sirePers != null && !sirePers.isBlank()) {
            return sirePers;
        }
        if (r < 64 && damPers != null && !damPers.isBlank()) {
            return damPers;
        }
        return PERSONALITIES[random.nextInt(PERSONALITIES.length)];
    }

    public static int getTemperamentIndex(String temp) {
        if (TEMP_GENTLE.equalsIgnoreCase(temp)) return 0;
        if (TEMP_NORMAL.equalsIgnoreCase(temp)) return 1;
        if (TEMP_ROUGH.equalsIgnoreCase(temp)) return 2;
        if (TEMP_TEMPERAMENTAL.equalsIgnoreCase(temp)) return 3;
        if (TEMP_VOLATILE.equalsIgnoreCase(temp) || "Halo's Reincarnation".equalsIgnoreCase(temp)) return 4;
        return 1;
    }

    public boolean isVolatile() {
        return TEMP_VOLATILE.equalsIgnoreCase(this.temperament) || "Halo's Reincarnation".equalsIgnoreCase(this.temperament);
    }

    public String getFormattedTemperament() {
        if (TEMP_GENTLE.equalsIgnoreCase(this.temperament)) return "§a" + localizeTemperament(TEMP_GENTLE);
        if (TEMP_NORMAL.equalsIgnoreCase(this.temperament)) return "§f" + localizeTemperament(TEMP_NORMAL);
        if (TEMP_ROUGH.equalsIgnoreCase(this.temperament)) return "§6" + localizeTemperament(TEMP_ROUGH);
        if (TEMP_TEMPERAMENTAL.equalsIgnoreCase(this.temperament)) return "§c" + localizeTemperament(TEMP_TEMPERAMENTAL);
        if (isVolatile()) return "§4§l" + localizeTemperament(TEMP_VOLATILE);
        return "§f" + localizeTemperament(this.temperament);
    }

    public String getMoodTierName() {
        int m = (int) Math.round(this.mood);
        if (m <= 20) return tr("Awful", "極差", "极差", "絶不調");
        if (m <= 40) return tr("Bad", "不佳", "不佳", "不調");
        if (m <= 60) return tr("Normal", "普通", "普通", "普通");
        if (m <= 80) return tr("Good", "良好", "良好", "好調");
        return tr("Great", "極佳", "极佳", "絶好調");
    }

    public String getFormattedMoodTier() {
        int m = (int) Math.round(this.mood);
        if (m <= 20) return "§4" + tr("Awful", "極差", "极差", "絶不調");
        if (m <= 40) return "§c" + tr("Bad", "不佳", "不佳", "不調");
        if (m <= 60) return "§e" + tr("Normal", "普通", "普通", "普通");
        if (m <= 80) return "§a" + tr("Good", "良好", "良好", "好調");
        return "§d§l" + tr("Great", "極佳", "极佳", "絶好調");
    }

    public static int getMoodTierColor(float moodVal) {
        int m = Math.round(moodVal);
        if (m <= 20) return 0xFFAA2222;
        if (m <= 40) return 0xFFFF5555;
        if (m <= 60) return 0xFFFFD800;
        if (m <= 80) return 0xFF55FF55;
        return 0xFFFF66CC;
    }

    public double getMoodStatMultiplier() {
        int m = (int) Math.round(this.mood);
        if (m <= 20) return 0.94;
        if (m <= 40) return 0.97;
        if (m <= 60) return 1.00;
        if (m <= 80) return 1.02;
        return 1.04;
    }

    public double getMoodExpMultiplier() {
        int m = (int) Math.round(this.mood);
        boolean quirky = PERS_QUIRKY.equalsIgnoreCase(this.personality);
        if (m <= 20) return quirky ? 0.50 : 0.75;
        if (m <= 40) return quirky ? 0.80 : 0.90;
        if (m <= 60) return 1.00;
        if (m <= 80) return quirky ? 1.25 : 1.10;
        return quirky ? 1.50 : 1.20;
    }

    public String getPersonality() {
        return (this.personality != null && !this.personality.isBlank()) ? this.personality : PERS_COMPETITIVE;
    }

    public void setPersonality(String personality) {
        if (personality == null || personality.isBlank()) return;
        for (String p : PERSONALITIES) {
            if (p.equalsIgnoreCase(personality.trim())) {
                this.personality = p;
                if (!PERS_COMPETITIVE.equalsIgnoreCase(p)) {
                    this.competitiveLoserBoost = false;
                }
                return;
            }
        }
        this.personality = personality.trim();
    }

    public boolean hasPersonality(String target) {
        return target != null && target.equalsIgnoreCase(getPersonality());
    }

    public String getFormattedPersonality() {
        String p = getPersonality();
        String loc = localizePersonality(p);
        String base = switch (p) {
            case PERS_COMPETITIVE   -> "§c" + loc;
            case PERS_LONE_WOLF     -> "§9" + loc;
            case PERS_CROWD_PLEASER -> "§d" + loc;
            case PERS_LAID_BACK     -> "§a" + loc;
            case PERS_GLUTTON       -> "§6" + loc;
            case PERS_STOIC         -> "§b" + loc;
            case PERS_CLINGY        -> "§5" + loc;
            case PERS_TIMID         -> "§e" + loc;
            case PERS_DEFEATIST     -> "§8" + loc;
            case PERS_QUIRKY        -> "§3" + loc;
            default                 -> "§f" + loc;
        };
        if (PERS_COMPETITIVE.equalsIgnoreCase(p) && this.competitiveLoserBoost) {
            base += " §6§l[🔥 " + tr("Determined", "鬥志燃燒", "斗志燃烧", "闘志燃焼") + "]";
        }
        return base;
    }

    public boolean hasCompetitiveLoserBoost() {
        return PERS_COMPETITIVE.equalsIgnoreCase(this.personality) && this.competitiveLoserBoost;
    }

    public void setCompetitiveLoserBoost(boolean active) {
        this.competitiveLoserBoost = active;
    }

    public int getOverfedStreak() {
        return this.overfedStreak;
    }

    public void setOverfedStreak(int streak) {
        this.overfedStreak = Math.max(0, streak);
    }

    public double getOverweightExerciseMeters() {
        return this.overweightExerciseMeters;
    }

    public void setOverweightExerciseMeters(double meters) {
        this.overweightExerciseMeters = Math.max(0.0, meters);
    }

    public long getLastOwnerInteractionMcDay() {
        return this.lastOwnerInteractionMcDay;
    }

    public void setLastOwnerInteractionMcDay(long mcDay) {
        this.lastOwnerInteractionMcDay = mcDay;
    }

    public boolean isGelding() {
        return GENDER_GELDING.equalsIgnoreCase(this.gender);
    }

    /**
     * Performs the instant Gelding operation on a male horse (Foal, Yearling, active Stallion, or retired Stud):
     * - Permanently sets gender to "Gelding" (cannot reproduce)
     * - Grants minor bonuses to Speed and Power (+2.5 display / +25 internal to current & max)
     * - Drops Temperament by 2 levels if gelded as a Foal or Yearling, or 1 level if gelded at 2 Equine Years or older!
     */
    public void geldHorse() {
        this.gender = GENDER_GELDING;
        this.inHeat = false;
        this.manualHeatOverride = false;
        this.pregnant = false;

        // Minor bonuses to Speed and Power (+25 internal = +2.5 display points)
        this.speedMax     = clampStat(this.speedMax + 25);
        this.speedCurrent = Math.min(this.speedMax, clampStat(this.speedCurrent + 25));
        this.powerMax     = clampPowerStat(this.powerMax + 25);
        this.powerCurrent = Math.min(this.powerMax, clampPowerStat(this.powerCurrent + 25));

        // Temperament drop: 2 levels if Foal or Yearling, 1 level if 2 Equine Years or older
        int curTempIdx = getTemperamentIndex(this.temperament);
        int dropLevels = isUnderTwoEquineYears() ? 2 : 1;
        int newTempIdx = Mth.clamp(curTempIdx - dropLevels, 0, TEMPERAMENTS.length - 1);
        this.temperament = TEMPERAMENTS[newTempIdx];
    }

    private double getPersonalityBaseStatBonusMultiplier() {
        double mult = 1.0;
        if (hasCompetitiveLoserBoost()) {
            mult += 0.03; // Competitive post-loss determination bonus until next win!
        }
        if (PERS_CLINGY.equalsIgnoreCase(this.personality) && this.bond >= 90.0) {
            mult += 0.025; // Clingy high-bond bonus!
        }
        return mult;
    }

    public int getEffectiveSpeedCurrent() {
        return (int) Math.round(this.speedCurrent * getPersonalityBaseStatBonusMultiplier());
    }

    public int getEffectiveStaminaCurrent() {
        return (int) Math.round(this.staminaCurrent * getPersonalityBaseStatBonusMultiplier());
    }

    public int getEffectivePowerCurrent() {
        return (int) Math.round(this.powerCurrent * getPersonalityBaseStatBonusMultiplier());
    }

    public int getEffectiveGutsCurrent() {
        return (int) Math.round(this.gutsCurrent * getPersonalityBaseStatBonusMultiplier());
    }

    public int getEffectiveWitsCurrent() {
        return (int) Math.round(this.witsCurrent * getPersonalityBaseStatBonusMultiplier());
    }

    public double getTemperamentBondExpMultiplier() {
        double b = Mth.clamp(this.bond / 100.0, 0.0, 1.0);
        if (TEMP_GENTLE.equalsIgnoreCase(this.temperament)) {
            return Mth.lerp(b, 1.00, 1.20);
        } else if (TEMP_NORMAL.equalsIgnoreCase(this.temperament)) {
            return Mth.lerp(b, 0.90, 1.40);
        } else if (TEMP_ROUGH.equalsIgnoreCase(this.temperament)) {
            return Mth.lerp(b, 0.70, 1.80);
        } else if (TEMP_TEMPERAMENTAL.equalsIgnoreCase(this.temperament)) {
            return Mth.lerp(b, 0.50, 2.00);
        } else if (isVolatile()) {
            return Mth.lerp(b, 0.30, 5.00);
        }
        return 1.00;
    }

    public double getTrainingMoveRefusalChance() {
        double b = Mth.clamp(this.bond / 100.0, 0.0, 1.0);
        if (TEMP_ROUGH.equalsIgnoreCase(this.temperament)) {
            return Mth.lerp(b, 0.05, 0.005);
        } else if (TEMP_TEMPERAMENTAL.equalsIgnoreCase(this.temperament)) {
            return Mth.lerp(b, 0.20, 0.01);
        } else if (isVolatile()) {
            return Mth.lerp(b, 0.50, 0.10);
        }
        return 0.0;
    }

    public double getStartingGateRearChance() {
        double chance = 0.003;
        if (TEMP_GENTLE.equalsIgnoreCase(this.temperament)) chance = 0.0;
        else if (TEMP_NORMAL.equalsIgnoreCase(this.temperament)) chance = 0.003;
        else if (TEMP_ROUGH.equalsIgnoreCase(this.temperament)) chance = 0.010;
        else if (TEMP_TEMPERAMENTAL.equalsIgnoreCase(this.temperament)) chance = 0.050;
        else if (isVolatile()) chance = 0.100;
        if (hasGeneticTrait(TRAIT_LIGHTNING_STARTER)) {
            chance *= 0.50;
        }
        return chance;
    }

    public double getCropPanicMultiplier() {
        double tempMult = 1.0;
        if (TEMP_GENTLE.equalsIgnoreCase(this.temperament)) tempMult = 0.70;
        else if (TEMP_NORMAL.equalsIgnoreCase(this.temperament)) tempMult = 1.00;
        else if (TEMP_ROUGH.equalsIgnoreCase(this.temperament)) tempMult = 1.30;
        else if (TEMP_TEMPERAMENTAL.equalsIgnoreCase(this.temperament)) tempMult = 1.65;
        else if (isVolatile()) tempMult = 2.10;

        int m = (int) Math.round(this.mood);
        double moodMult = 1.0;
        if (m <= 20) moodMult = 1.25;
        else if (m <= 40) moodMult = 1.10;
        else if (m >= 81) moodMult = 0.90;

        if (hasGeneticTrait(TRAIT_HIGH_STRUNG)) {
            tempMult *= 1.30;
        }
        return tempMult * moodMult;
    }

    private static int clampStat(int value) {
        return Math.max(50, Math.min(1150, value));
    }

    private int clampPowerStat(int value) {
        int cap = hasGeneticTrait(TRAIT_LARGE_HOOVES) ? 2000 : 1150;
        return Math.max(50, Math.min(cap, value));
    }

    public static double getExpRequiredForNextPoint(int currentStatInternal) {
        double norm = Mth.clamp(currentStatInternal / 1000.0, 0.0, 1.0);
        return 30.0 + Math.pow(norm, 1.6) * 150.0;
    }

    public boolean addSpeedExp(double amount) {
        if (amount <= 0.0 || this.speedCurrent >= this.speedMax) {
            this.speedExp = 0.0;
            return false;
        }
        this.speedExp += amount;
        boolean leveledUp = false;
        while (this.speedCurrent < this.speedMax) {
            double req = getExpRequiredForNextPoint(this.speedCurrent);
            if (this.speedExp >= req) {
                this.speedExp -= req;
                this.speedCurrent++;
                leveledUp = true;
            } else {
                break;
            }
        }
        if (this.speedCurrent >= this.speedMax) {
            this.speedExp = 0.0;
        }
        return leveledUp;
    }

    public boolean addStaminaExp(double amount) {
        if (amount <= 0.0 || this.staminaCurrent >= this.staminaMax) {
            this.staminaExp = 0.0;
            return false;
        }
        this.staminaExp += amount;
        boolean leveledUp = false;
        while (this.staminaCurrent < this.staminaMax) {
            double req = getExpRequiredForNextPoint(this.staminaCurrent);
            if (this.staminaExp >= req) {
                this.staminaExp -= req;
                this.staminaCurrent++;
                leveledUp = true;
            } else {
                break;
            }
        }
        if (this.staminaCurrent >= this.staminaMax) {
            this.staminaExp = 0.0;
        }
        return leveledUp;
    }

    public boolean addPowerExp(double amount) {
        if (amount <= 0.0 || this.powerCurrent >= this.powerMax) {
            this.powerExp = 0.0;
            return false;
        }
        this.powerExp += amount;
        boolean leveledUp = false;
        while (this.powerCurrent < this.powerMax) {
            double req = getExpRequiredForNextPoint(this.powerCurrent);
            if (this.powerExp >= req) {
                this.powerExp -= req;
                this.powerCurrent++;
                leveledUp = true;
            } else {
                break;
            }
        }
        if (this.powerCurrent >= this.powerMax) {
            this.powerExp = 0.0;
        }
        return leveledUp;
    }

    public boolean addGutsExp(double amount) {
        if (amount <= 0.0 || this.gutsCurrent >= this.gutsMax) {
            this.gutsExp = 0.0;
            return false;
        }
        this.gutsExp += amount;
        boolean leveledUp = false;
        while (this.gutsCurrent < this.gutsMax) {
            double req = getExpRequiredForNextPoint(this.gutsCurrent);
            if (this.gutsExp >= req) {
                this.gutsExp -= req;
                this.gutsCurrent++;
                leveledUp = true;
            } else {
                break;
            }
        }
        if (this.gutsCurrent >= this.gutsMax) {
            this.gutsExp = 0.0;
        }
        return leveledUp;
    }

    public boolean addWitsExp(double amount) {
        if (amount <= 0.0 || this.witsCurrent >= this.witsMax) {
            this.witsExp = 0.0;
            return false;
        }
        this.witsExp += amount;
        boolean leveledUp = false;
        while (this.witsCurrent < this.witsMax) {
            double req = getExpRequiredForNextPoint(this.witsCurrent);
            if (this.witsExp >= req) {
                this.witsExp -= req;
                this.witsCurrent++;
                leveledUp = true;
            } else {
                break;
            }
        }
        if (this.witsCurrent >= this.witsMax) {
            this.witsExp = 0.0;
        }
        return leveledUp;
    }

    public double getSpeedExp() { return speedExp; }
    public double getStaminaExp() { return staminaExp; }
    public double getPowerExp() { return powerExp; }
    public double getGutsExp() { return gutsExp; }
    public double getWitsExp() { return witsExp; }

    public boolean hasEnchantedAppleBlessing() {
        return System.currentTimeMillis() < this.enchantedAppleExpiryMillis;
    }

    public long getEnchantedAppleExpiryMillis() {
        return this.enchantedAppleExpiryMillis;
    }

    public void setEnchantedAppleBlessing24Hours() {
        this.enchantedAppleExpiryMillis = System.currentTimeMillis() + 86400000L;
    }

    public int getEffectiveMinDistance() {
        int reduction;
        if (this.minDistance <= 1400) {
            reduction = 400;
        } else if (this.minDistance <= 2200) {
            reduction = 500;
        } else {
            reduction = 600;
        }
        return Math.max(600, this.minDistance - reduction);
    }

    public double getMaxStaminaPool() {
        double effStamina = getEffectiveStaminaCurrent() * getMoodStatMultiplier();
        double pool = 1200.0 + (effStamina / 1000.0) * 1600.0;
        if (hasGeneticTrait(TRAIT_X_FACTOR)) {
            // Active X-Factor (Massive Heart): +15% Maximum Stamina Pool (does not increase Speed or Power!)
            pool *= 1.15;
        }
        return pool;
    }

    public double getMaxGutsPool() {
        double effGuts = getEffectiveGutsCurrent() * getMoodStatMultiplier();
        if (hasAilment(AILMENT_FEVER)) {
            effGuts *= 0.50;
        }
        double pool = 90.0 + (effGuts / 1000.0) * 1600.0 / 10.0;
        if (hasGeneticTrait(TRAIT_X_FACTOR)) {
            // Active X-Factor (Massive Heart): +15% Maximum Guts Pool
            pool *= 1.15;
        }
        if (hasAilment(AILMENT_EIPH)) {
            pool *= 0.50;
        }
        if (hasGeneticTrait(TRAIT_SOFT_PALATE) && !this.blinkersHood) {
            pool *= 0.75; // Soft Palate Displacement reduces max Guts pool by 25% unless fitted with Blinkers/Hood!
        }
        return pool;
    }

    public static String formatStatPair(int current, int max) {
        return String.format(Locale.ROOT, "%.1f / %.1f", current / 10.0, max / 10.0);
    }

    public String getTrackAptitudeBar() {
        int turf = Math.max(0, Math.min(10, this.turfAptitude));
        int dirt = Math.max(0, Math.min(10, this.dirtAptitude));

        StringBuilder sb = new StringBuilder();
        sb.append("(").append(turf).append(") ").append(tr("TURF", "草地", "草地", "芝")).append(" [");
        for (int i = 0; i < 10 - turf; i++) {
            sb.append('=');
        }
        for (int i = 0; i < turf + dirt; i++) {
            sb.append('■');
        }
        for (int i = 0; i < 10 - dirt; i++) {
            sb.append('=');
        }
        sb.append("] ").append(tr("DIRT", "泥地", "泥地", "ダート")).append(" (").append(dirt).append(")");
        return sb.toString();
    }

    public String getDistanceAptitudeBar() {
        StringBuilder sb = new StringBuilder();
        sb.append(tr("SPRINT", "短途", "短途", "短距離")).append(" [");
        for (int i = 0; i <= 30; i++) {
            int dist = 1000 + i * 100;
            if (i == 30 && this.maxDistance > 4000) {
                sb.append('▣');
            } else if (dist >= this.minDistance && dist <= this.maxDistance) {
                sb.append('■');
            } else {
                sb.append('=');
            }
        }
        sb.append("] ").append(tr("LONG", "長途", "长途", "長距離")).append(" (").append(this.minDistance).append(" - ").append(this.maxDistance).append("m)");
        return sb.toString();
    }

    // =========================================================================
    // CAREER HISTORY & EARNINGS HELPERS
    // =========================================================================
    public boolean hasWonDebutOrMaiden() {
        for (CareerRaceRecord r : this.careerHistory) {
            if (r.placement == 1) {
                return true;
            }
        }
        return false;
    }

    public List<CareerRaceRecord> getCareerHistory() {
        return careerHistory;
    }

    public void setCareerHistory(List<CareerRaceRecord> records) {
        if (records == this.careerHistory) {
            return;
        }
        this.careerHistory.clear();
        if (records != null) {
            this.careerHistory.addAll(records);
        }
    }

    public void addCareerRecord(CareerRaceRecord rec) {
        if (rec == null) return;
        this.careerHistory.add(rec);
        this.totalEarnings += Math.max(0L, rec.earnings);
        if (rec.placement == 1) {
            this.competitiveLoserBoost = false;
        } else if (rec.placement > 1 && PERS_COMPETITIVE.equalsIgnoreCase(this.personality)) {
            this.competitiveLoserBoost = true;
        }
    }

    public boolean removeCareerRecordsByRace(String targetRaceId, String fallbackRaceName) {
        boolean removedAny = false;
        Iterator<CareerRaceRecord> it = this.careerHistory.iterator();
        while (it.hasNext()) {
            CareerRaceRecord r = it.next();
            boolean matchId = targetRaceId != null && !targetRaceId.isEmpty() && targetRaceId.equals(r.raceId);
            boolean matchFallbackName = (r.raceId == null || r.raceId.isEmpty())
                    && fallbackRaceName != null && !fallbackRaceName.isEmpty()
                    && fallbackRaceName.equalsIgnoreCase(r.raceName);
            if (matchId || matchFallbackName) {
                this.totalEarnings = Math.max(0L, this.totalEarnings - Math.max(0L, r.earnings));
                it.remove();
                removedAny = true;
            }
        }
        return removedAny;
    }

    public void clearCareerHistory() {
        this.careerHistory.clear();
        this.totalEarnings = 0L;
    }

    public long getTotalEarnings() {
        return totalEarnings;
    }

    public void setTotalEarnings(long totalEarnings) {
        this.totalEarnings = Math.max(0L, totalEarnings);
    }

    public String getCareerRecordSummary() {
        int starts = careerHistory.size();
        int wins = 0;
        int seconds = 0;
        int thirds = 0;
        int unplaced = 0;
        for (CareerRaceRecord r : careerHistory) {
            if (r.placement == 1) wins++;
            else if (r.placement == 2) seconds++;
            else if (r.placement == 3) thirds++;
            else unplaced++;
        }
        return String.format(Locale.ROOT,
                "§e%s: §f%d §7(%d-%d-%d-%d)  §7|  §e%s: §a$%,d",
                tr("Starts", "出賽", "出赛", "戦績"),
                starts, wins, seconds, thirds, unplaced,
                tr("Total Earnings", "總獎金", "总奖金", "獲得賞金"),
                totalEarnings);
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Registered", this.registered);
        tag.putString("Name", this.name);
        tag.putString("Gender", this.gender);
        tag.putString("Strategy", this.strategy);
        tag.putString("Temperament", this.temperament);
        tag.putString("Personality", getPersonality());
        tag.putBoolean("CompetitiveLoserBoost", this.competitiveLoserBoost);
        tag.putInt("OverfedStreak", this.overfedStreak);
        tag.putDouble("OverweightExerciseMeters", this.overweightExerciseMeters);
        tag.putLong("LastOwnerInteractionMcDay", this.lastOwnerInteractionMcDay);

        tag.putString("OwnerUUID", this.ownerUUID != null ? this.ownerUUID : "");
        tag.putString("OwnerName", this.ownerName != null ? this.ownerName : "");
        tag.putString("OwnerSilkCode", this.ownerSilkCode != null ? this.ownerSilkCode : "");
        tag.putString("CropTag", this.cropTag != null ? this.cropTag : "");
        tag.putBoolean("Locked", this.locked);
        tag.putBoolean("HasStable", this.hasStable);
        tag.putDouble("StableX", this.stableX);
        tag.putDouble("StableY", this.stableY);
        tag.putDouble("StableZ", this.stableZ);
        tag.putString("StableDimension", this.stableDimension != null ? this.stableDimension : "minecraft:overworld");
        tag.putBoolean("InRace", this.inRace);
        tag.putBoolean("WearingRaceSilk", this.wearingRaceSilk);
        tag.putBoolean("DeathsDoor", this.deathsDoor);
        tag.putInt("BaseAdultEquineAgeYears", this.baseAdultEquineAgeYears);
        tag.putLong("AdultStartTimestampMillis", this.adultStartTimestampMillis);
        tag.putInt("EquineAgeYears", getEquineAgeYears());

        tag.putInt("SpeedCurrent", this.speedCurrent);
        tag.putInt("SpeedMax", this.speedMax);
        tag.putInt("StaminaCurrent", this.staminaCurrent);
        tag.putInt("StaminaMax", this.staminaMax);
        tag.putInt("PowerCurrent", this.powerCurrent);
        tag.putInt("PowerMax", this.powerMax);
        tag.putInt("GutsCurrent", this.gutsCurrent);
        tag.putInt("GutsMax", this.gutsMax);
        tag.putInt("WitsCurrent", this.witsCurrent);
        tag.putInt("WitsMax", this.witsMax);

        tag.putDouble("SpeedExp", this.speedExp);
        tag.putDouble("StaminaExp", this.staminaExp);
        tag.putDouble("PowerExp", this.powerExp);
        tag.putDouble("GutsExp", this.gutsExp);
        tag.putDouble("WitsExp", this.witsExp);

        tag.putDouble("Hunger", this.hunger);
        tag.putDouble("Thirst", this.thirst);
        tag.putDouble("Fatigue", this.fatigue);
        tag.putDouble("Bond", this.bond);
        tag.putDouble("Mood", this.mood);
        tag.putLong("EnchantedAppleExpiry", this.enchantedAppleExpiryMillis);

        tag.putBoolean("WeightedTack", this.weightedTack);
        tag.putBoolean("BlinkersHood", this.blinkersHood);

        tag.putInt("TurfAptitude", this.turfAptitude);
        tag.putInt("DirtAptitude", this.dirtAptitude);
        tag.putInt("MinDistance", this.minDistance);
        tag.putInt("MaxDistance", this.maxDistance);

        tag.putLong("TotalEarnings", this.totalEarnings);
        ListTag careerList = new ListTag();
        for (CareerRaceRecord r : this.careerHistory) {
            careerList.add(r.toNBT());
        }
        tag.put("CareerHistory", careerList);

        ListTag ailmentsTag = new ListTag();
        for (String a : this.activeAilments) {
            if (a != null && !a.isBlank()) {
                ailmentsTag.add(StringTag.valueOf(a));
            }
        }
        tag.put("ActiveAilments", ailmentsTag);
        tag.putString("ActiveInjury", this.activeInjury != null ? this.activeInjury : INJURY_NONE);
        tag.putDouble("InjuryDaysRemaining", this.injuryDaysRemaining);
        tag.putBoolean("VetTreatedThisInjury", this.vetTreatedThisInjury);
        tag.putBoolean("PoorPrognosis", this.poorPrognosis);
        tag.putBoolean("PoorPrognosisAppleImmunity", this.poorPrognosisAppleImmunity);
        tag.putInt("PoorPrognosisGoldenApplesUsed", this.poorPrognosisGoldenApplesUsed);
        tag.putBoolean("PoorPrognosisGoldenAppleActive", this.poorPrognosisGoldenAppleActive);
        tag.putInt("PoorPrognosisGoldenAppleDeathChance", this.poorPrognosisGoldenAppleDeathChance);
        tag.putBoolean("TriedToSaveWithApples", this.triedToSaveWithApples);
        tag.putBoolean("CareerEnded", this.careerEnded);
        tag.putLong("LastCheckedMinecraftDay", this.lastCheckedMinecraftDay);
        tag.putLong("LastCheckedDeathsDoorRealDay", this.lastCheckedDeathsDoorRealDay);

        // Step 18 NBT Serialization
        tag.putBoolean("Retired", this.retired);
        tag.putBoolean("InHeat", this.inHeat);
        tag.putBoolean("ManualHeatOverride", this.manualHeatOverride);
        tag.putBoolean("Pregnant", this.pregnant);
        tag.putLong("PregnancyConceptionMillis", this.pregnancyConceptionMillis);
        tag.putLong("PregnancyDurationMillis", this.pregnancyDurationMillis);
        tag.putString("PregnantSireName", this.pregnantSireName != null ? this.pregnantSireName : "");
        tag.putString("PregnantSireUUID", this.pregnantSireUUID != null ? this.pregnantSireUUID : "");
        if (this.pregnantSireNbt != null && !this.pregnantSireNbt.isEmpty()) {
            tag.put("PregnantSireNbt", this.pregnantSireNbt.copy());
        }

        tag.putBoolean("Foal", this.foal);
        tag.putBoolean("Yearling", this.yearling);
        tag.putBoolean("BredFoalPendingName", this.bredFoalPendingName);
        tag.putLong("BirthTimestampMillis", this.birthTimestampMillis);
        tag.putLong("FoalGrowDurationMillis", this.foalGrowDurationMillis);
        tag.putLong("YearlingStartMillis", this.yearlingStartMillis);
        tag.putLong("YearlingGrowDurationMillis", this.yearlingGrowDurationMillis);

        tag.putInt("HereditaryPoints", this.hereditaryPoints);
        tag.putByteArray("GeneSlots", this.geneSlots.clone());
        tag.putBoolean("AtavismTriggered", this.atavismTriggered);
        tag.putString("InbreedingSummary", this.inbreedingSummary != null ? this.inbreedingSummary : "Outcross (No Inbreeding)");
        tag.putBoolean("InbredDepressionTriggered", this.inbredDepressionTriggered);
        tag.putBoolean("PureWhiteCoat", this.pureWhiteCoat);
        tag.putInt("XFactorCopies", this.xFactorCopies);

        ListTag traitsTag = new ListTag();
        for (String tr : this.geneticTraits) {
            if (tr != null && !tr.isBlank()) {
                traitsTag.add(StringTag.valueOf(tr));
            }
        }
        tag.put("GeneticTraits", traitsTag);

        ListTag pedTag = new ListTag();
        for (int i = 0; i < PEDIGREE_SIZE; i++) {
            pedTag.add(StringTag.valueOf(this.pedigreeTree[i] != null ? this.pedigreeTree[i] : ""));
        }
        tag.put("PedigreeTree", pedTag);
        tag.putIntArray("PedigreeHP", this.pedigreeHP.clone());

        return tag;
    }

    public void loadFromNBT(CompoundTag tag) {
        this.registered = tag.getBoolean("Registered");
        this.name = tag.getString("Name");
        this.gender = tag.getString("Gender");
        this.strategy = tag.contains("Strategy") && !tag.getString("Strategy").isEmpty()
                ? tag.getString("Strategy") : STRAT_PACE_CHASER;
        String loadedTemp = tag.contains("Temperament") ? tag.getString("Temperament") : TEMP_NORMAL;
        if ("Halo's Reincarnation".equalsIgnoreCase(loadedTemp)) {
            loadedTemp = TEMP_VOLATILE;
        }
        this.temperament = loadedTemp;
        this.personality = tag.contains("Personality") && !tag.getString("Personality").isBlank()
                ? tag.getString("Personality") : PERS_COMPETITIVE;
        this.competitiveLoserBoost = tag.getBoolean("CompetitiveLoserBoost");
        this.overfedStreak = tag.getInt("OverfedStreak");
        this.overweightExerciseMeters = tag.getDouble("OverweightExerciseMeters");
        this.lastOwnerInteractionMcDay = tag.contains("LastOwnerInteractionMcDay") ? tag.getLong("LastOwnerInteractionMcDay") : -1L;

        this.ownerUUID = tag.contains("OwnerUUID") ? tag.getString("OwnerUUID") : "";
        this.ownerName = tag.contains("OwnerName") ? tag.getString("OwnerName") : "";
        this.ownerSilkCode = tag.contains("OwnerSilkCode") ? tag.getString("OwnerSilkCode") : "";
        this.cropTag = tag.contains("CropTag") ? tag.getString("CropTag") : "";
        this.locked = tag.getBoolean("Locked");
        this.hasStable = tag.getBoolean("HasStable");
        this.stableX = tag.getDouble("StableX");
        this.stableY = tag.getDouble("StableY");
        this.stableZ = tag.getDouble("StableZ");
        this.stableDimension = tag.contains("StableDimension") ? tag.getString("StableDimension") : "minecraft:overworld";
        this.inRace = tag.getBoolean("InRace");
        this.wearingRaceSilk = tag.getBoolean("WearingRaceSilk");
        this.deathsDoor = tag.getBoolean("DeathsDoor");
        this.baseAdultEquineAgeYears = tag.contains("BaseAdultEquineAgeYears") ? Math.max(2, tag.getInt("BaseAdultEquineAgeYears")) : 2;
        this.adultStartTimestampMillis = tag.getLong("AdultStartTimestampMillis");
        this.cachedEquineAgeYears = tag.contains("EquineAgeYears") ? Math.max(0, tag.getInt("EquineAgeYears")) : (this.foal ? 0 : (this.yearling ? 1 : this.baseAdultEquineAgeYears));

        this.speedCurrent = tag.getInt("SpeedCurrent");
        this.speedMax = tag.getInt("SpeedMax");
        this.staminaCurrent = tag.getInt("StaminaCurrent");
        this.staminaMax = tag.getInt("StaminaMax");
        this.powerCurrent = tag.getInt("PowerCurrent");
        this.powerMax = tag.getInt("PowerMax");
        this.gutsCurrent = tag.getInt("GutsCurrent");
        this.gutsMax = tag.getInt("GutsMax");
        this.witsCurrent = tag.getInt("WitsCurrent");
        this.witsMax = tag.getInt("WitsMax");

        this.speedExp = tag.getDouble("SpeedExp");
        this.staminaExp = tag.getDouble("StaminaExp");
        this.powerExp = tag.getDouble("PowerExp");
        this.gutsExp = tag.getDouble("GutsExp");
        this.witsExp = tag.getDouble("WitsExp");

        this.hunger = tag.contains("Hunger") ? tag.getDouble("Hunger") : 100.0;
        this.thirst = tag.contains("Thirst") ? tag.getDouble("Thirst") : 100.0;
        this.fatigue = tag.contains("Fatigue") ? tag.getDouble("Fatigue") : 0.0;
        this.bond = tag.contains("Bond") ? tag.getDouble("Bond") : 20.0;
        this.mood = tag.contains("Mood") ? tag.getDouble("Mood") : 55.0;
        this.enchantedAppleExpiryMillis = tag.getLong("EnchantedAppleExpiry");

        this.weightedTack = tag.getBoolean("WeightedTack");
        this.blinkersHood = tag.getBoolean("BlinkersHood");

        this.turfAptitude = tag.getInt("TurfAptitude");
        this.dirtAptitude = tag.getInt("DirtAptitude");
        this.minDistance = tag.getInt("MinDistance");
        this.maxDistance = tag.getInt("MaxDistance");

        this.totalEarnings = Math.max(0L, tag.getLong("TotalEarnings"));
        this.careerHistory.clear();
        if (tag.contains("CareerHistory", Tag.TAG_LIST)) {
            ListTag careerList = tag.getList("CareerHistory", Tag.TAG_COMPOUND);
            for (int i = 0; i < careerList.size(); i++) {
                this.careerHistory.add(CareerRaceRecord.fromNBT(careerList.getCompound(i)));
            }
        }

        this.activeAilments.clear();
        if (tag.contains("ActiveAilments", Tag.TAG_LIST)) {
            ListTag ailmentsTag = tag.getList("ActiveAilments", Tag.TAG_STRING);
            for (int i = 0; i < ailmentsTag.size(); i++) {
                String a = ailmentsTag.getString(i);
                if (a != null && !a.isBlank() && !this.activeAilments.contains(a)) {
                    this.activeAilments.add(a);
                }
            }
        }
        this.activeInjury = tag.contains("ActiveInjury") ? tag.getString("ActiveInjury") : INJURY_NONE;
        this.injuryDaysRemaining = Math.max(0.0, tag.getDouble("InjuryDaysRemaining"));
        this.vetTreatedThisInjury = tag.getBoolean("VetTreatedThisInjury");
        this.poorPrognosis = tag.getBoolean("PoorPrognosis")
                || INJURY_CATASTROPHIC_FRACTURE.equalsIgnoreCase(this.activeInjury)
                || hasAilment(AILMENT_POOR_PROGNOSIS);
        this.poorPrognosisAppleImmunity = tag.getBoolean("PoorPrognosisAppleImmunity");
        this.poorPrognosisGoldenApplesUsed = Math.max(0, tag.getInt("PoorPrognosisGoldenApplesUsed"));
        this.poorPrognosisGoldenAppleActive = tag.getBoolean("PoorPrognosisGoldenAppleActive");
        this.poorPrognosisGoldenAppleDeathChance = tag.contains("PoorPrognosisGoldenAppleDeathChance")
                ? Mth.clamp(tag.getInt("PoorPrognosisGoldenAppleDeathChance"), 0, 49) : 49;
        this.triedToSaveWithApples = tag.getBoolean("TriedToSaveWithApples");
        this.careerEnded = tag.getBoolean("CareerEnded");
        this.lastCheckedMinecraftDay = tag.contains("LastCheckedMinecraftDay") ? tag.getLong("LastCheckedMinecraftDay") : -1L;
        this.lastCheckedDeathsDoorRealDay = tag.contains("LastCheckedDeathsDoorRealDay") ? tag.getLong("LastCheckedDeathsDoorRealDay") : -1L;

        // Step 18 NBT Deserialization
        this.retired = tag.getBoolean("Retired");
        this.inHeat = tag.getBoolean("InHeat");
        this.manualHeatOverride = tag.getBoolean("ManualHeatOverride");
        this.pregnant = tag.getBoolean("Pregnant");
        this.pregnancyConceptionMillis = tag.getLong("PregnancyConceptionMillis");
        this.pregnancyDurationMillis = tag.getLong("PregnancyDurationMillis");
        this.pregnantSireName = tag.getString("PregnantSireName");
        this.pregnantSireUUID = tag.getString("PregnantSireUUID");
        this.pregnantSireNbt = tag.contains("PregnantSireNbt") ? tag.getCompound("PregnantSireNbt").copy() : new CompoundTag();

        this.foal = tag.getBoolean("Foal");
        this.yearling = tag.getBoolean("Yearling");
        this.bredFoalPendingName = tag.getBoolean("BredFoalPendingName");
        this.birthTimestampMillis = tag.getLong("BirthTimestampMillis");
        this.foalGrowDurationMillis = tag.getLong("FoalGrowDurationMillis");
        this.yearlingStartMillis = tag.getLong("YearlingStartMillis");
        this.yearlingGrowDurationMillis = tag.getLong("YearlingGrowDurationMillis");

        this.hereditaryPoints = tag.contains("HereditaryPoints") ? Mth.clamp(tag.getInt("HereditaryPoints"), 0, 100) : 68;
        if (tag.contains("GeneSlots", Tag.TAG_BYTE_ARRAY)) {
            byte[] loadedGenes = tag.getByteArray("GeneSlots");
            for (int i = 0; i < TOTAL_HP_SLOTS; i++) {
                this.geneSlots[i] = (i < loadedGenes.length) ? loadedGenes[i] : 0;
            }
        } else {
            initGeneSlotsFromHP(this.hereditaryPoints, RandomSource.create());
        }
        this.atavismTriggered = tag.getBoolean("AtavismTriggered");
        this.inbreedingSummary = tag.contains("InbreedingSummary") && !tag.getString("InbreedingSummary").isEmpty()
                ? tag.getString("InbreedingSummary") : "Outcross (No Inbreeding)";
        this.inbredDepressionTriggered = tag.getBoolean("InbredDepressionTriggered");
        this.pureWhiteCoat = tag.getBoolean("PureWhiteCoat");

        this.geneticTraits.clear();
        if (tag.contains("GeneticTraits", Tag.TAG_LIST)) {
            ListTag traitsTag = tag.getList("GeneticTraits", Tag.TAG_STRING);
            for (int i = 0; i < traitsTag.size(); i++) {
                String tr = traitsTag.getString(i);
                if ("Large Heart".equalsIgnoreCase(tr)) {
                    tr = TRAIT_X_FACTOR;
                }
                if (tr != null && !tr.isBlank() && !this.geneticTraits.contains(tr)) {
                    this.geneticTraits.add(tr);
                }
            }
        }
        int maxCopiesForSex = "Female".equalsIgnoreCase(this.gender) ? 2 : 1;
        this.xFactorCopies = tag.contains("XFactorCopies")
                ? Mth.clamp(tag.getInt("XFactorCopies"), 0, maxCopiesForSex)
                : (hasGeneticTrait(TRAIT_X_FACTOR) ? 1 : 0);
        if (hasGeneticTrait(TRAIT_X_FACTOR) && this.xFactorCopies <= 0) {
            this.xFactorCopies = 1;
        }

        for (int i = 0; i < PEDIGREE_SIZE; i++) {
            this.pedigreeTree[i] = "";
            this.pedigreeHP[i] = 65;
        }
        if (tag.contains("PedigreeTree", Tag.TAG_LIST)) {
            ListTag pedTag = tag.getList("PedigreeTree", Tag.TAG_STRING);
            for (int i = 0; i < Math.min(PEDIGREE_SIZE, pedTag.size()); i++) {
                this.pedigreeTree[i] = pedTag.getString(i);
            }
        }
        if (tag.contains("PedigreeHP", Tag.TAG_INT_ARRAY)) {
            int[] loadedHp = tag.getIntArray("PedigreeHP");
            for (int i = 0; i < Math.min(PEDIGREE_SIZE, loadedHp.length); i++) {
                this.pedigreeHP[i] = Mth.clamp(loadedHp[i], 0, 100);
            }
        }

        enforceHealthMoodAndFatigueRules();
    }

    public boolean hasOwner() {
        return this.ownerUUID != null && !this.ownerUUID.isEmpty();
    }

    public boolean isOwnedBy(UUID playerUUID) {
        return playerUUID != null && hasOwner() && this.ownerUUID.equalsIgnoreCase(playerUUID.toString());
    }

    public String getOwnerUUID() { return ownerUUID; }
    public String getOwnerName() { return ownerName; }

    public void setOwner(UUID uuid, String playerName) {
        this.ownerUUID = uuid != null ? uuid.toString() : "";
        this.ownerName = playerName != null ? playerName : "";
    }

    public void clearOwner() {
        this.ownerUUID = "";
        this.ownerName = "";
        this.ownerSilkCode = "";
        this.locked = false;
    }

    public String getOwnerSilkCode() {
        return this.ownerSilkCode != null ? this.ownerSilkCode : "";
    }

    public void setOwnerSilkCode(String silkCode) {
        this.ownerSilkCode = silkCode != null ? silkCode.trim() : "";
    }

    public boolean hasOwnerSilk() {
        return this.ownerSilkCode != null && !this.ownerSilkCode.isBlank();
    }

    public String getCropTag() {
        return (this.cropTag != null && !this.cropTag.isBlank()) ? this.cropTag : String.valueOf( java.time.LocalDate.now().getYear() );
    }

    public void setCropTag(String cropTag) {
        this.cropTag = cropTag != null ? cropTag.trim() : "";
    }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public boolean hasStable() { return hasStable; }
    public double getStableX() { return stableX; }
    public double getStableY() { return stableY; }
    public double getStableZ() { return stableZ; }
    public String getStableDimension() { return stableDimension; }

    public void setStable(double x, double y, double z, String dimension) {
        this.hasStable = true;
        this.stableX = x;
        this.stableY = y;
        this.stableZ = z;
        this.stableDimension = dimension != null ? dimension : "minecraft:overworld";
    }

    public boolean isInRace() { return inRace; }
    public void setInRace(boolean inRace) {
        this.inRace = inRace;
        if (inRace) {
            this.wearingRaceSilk = true;
        }
    }

    public boolean isWearingRaceSilk() { return wearingRaceSilk; }
    public void setWearingRaceSilk(boolean wearingRaceSilk) { this.wearingRaceSilk = wearingRaceSilk; }

    public boolean isAtDeathsDoor() { return deathsDoor; }
    public void setAtDeathsDoor(boolean deathsDoor) {
        this.deathsDoor = deathsDoor;
        if (deathsDoor) {
            this.mood = Math.min(this.mood, 25.0);
        }
    }

    public int getEquineAgeYears() {
        if (this.foal) return 0;
        if (this.yearling) return 1;
        return Math.max(2, this.cachedEquineAgeYears);
    }

    public int getEquineAgeYears(StudbookSavedData studbook) {
        if (this.foal) {
            this.cachedEquineAgeYears = 0;
            return 0;
        }
        if (this.yearling) {
            this.cachedEquineAgeYears = 1;
            return 1;
        }
        if (this.adultStartTimestampMillis <= 0L) {
            this.adultStartTimestampMillis = System.currentTimeMillis();
        }
        long oneYearMillis = (studbook != null) ? studbook.getFoalGrowthDurationMillis() : (365L * 86_400_000L);
        long elapsed = Math.max(0L, System.currentTimeMillis() - this.adultStartTimestampMillis);
        int elapsedYears = (oneYearMillis > 0L) ? (int) (elapsed / oneYearMillis) : 0;
        this.cachedEquineAgeYears = Math.max(2, this.baseAdultEquineAgeYears + elapsedYears);
        return this.cachedEquineAgeYears;
    }

    public void setEquineAgeYears(int ageYears, long oneEquineYearMillis) {
        int clamped = Math.max(0, ageYears);
        long dur = Math.max(60_000L, oneEquineYearMillis);
        if (clamped == 0) {
            startFoalGrowth(dur);
            this.baseAdultEquineAgeYears = 2;
            this.adultStartTimestampMillis = 0L;
            this.cachedEquineAgeYears = 0;
        } else if (clamped == 1) {
            this.foal = false;
            this.yearling = true;
            this.yearlingStartMillis = System.currentTimeMillis();
            this.yearlingGrowDurationMillis = dur;
            this.baseAdultEquineAgeYears = 2;
            this.adultStartTimestampMillis = 0L;
            this.cachedEquineAgeYears = 1;
        } else {
            this.foal = false;
            this.yearling = false;
            this.baseAdultEquineAgeYears = clamped;
            this.adultStartTimestampMillis = System.currentTimeMillis();
            this.cachedEquineAgeYears = clamped;
        }
    }

    public boolean isRegistered() { return registered; }
    public String getName() { return name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public String getTemperament() { return temperament; }
    public void setTemperament(String temperament) { this.temperament = temperament; }

    public int getSpeedCurrent() { return speedCurrent; }
    public void setSpeedCurrent(int v) { this.speedCurrent = clampStat(v); }
    public int getSpeedMax() { return speedMax; }
    public void setSpeedMax(int v) { this.speedMax = clampStat(v); }

    public int getStaminaCurrent() { return staminaCurrent; }
    public void setStaminaCurrent(int v) { this.staminaCurrent = clampStat(v); }
    public int getStaminaMax() { return staminaMax; }
    public void setStaminaMax(int v) { this.staminaMax = clampStat(v); }

    public int getPowerCurrent() { return powerCurrent; }
    public void setPowerCurrent(int v) { this.powerCurrent = clampPowerStat(v); }
    public int getPowerMax() { return powerMax; }
    public void setPowerMax(int v) { this.powerMax = clampPowerStat(v); }

    public int getGutsCurrent() { return gutsCurrent; }
    public void setGutsCurrent(int v) { this.gutsCurrent = clampStat(v); }
    public int getGutsMax() { return gutsMax; }
    public void setGutsMax(int v) { this.gutsMax = clampStat(v); }

    public int getWitsCurrent() { return witsCurrent; }
    public void setWitsCurrent(int v) { this.witsCurrent = clampStat(v); }
    public int getWitsMax() { return witsMax; }
    public void setWitsMax(int v) { this.witsMax = clampStat(v); }

    public double getHunger() { return hunger; }
    public void setHunger(double v) { this.hunger = Mth.clamp(v, 0.0, 100.0); }

    public double getThirst() { return thirst; }
    public void setThirst(double v) { this.thirst = Mth.clamp(v, 0.0, 100.0); }

    public double getFatigue() { return fatigue; }
    public void setFatigue(double v) {
        this.fatigue = Mth.clamp(v, 0.0, 100.0);
        if (this.fatigue >= 80.0) {
            if (!hasAilment(AILMENT_UNSTABLE_LEGS)) {
                this.activeAilments.add(AILMENT_UNSTABLE_LEGS);
            }
        } else {
            this.activeAilments.removeIf(a -> AILMENT_UNSTABLE_LEGS.equalsIgnoreCase(a));
        }
        enforceMoodCap();
    }

    public double getBond() { return bond; }
    public void setBond(double v) { this.bond = Mth.clamp(v, 0.0, 100.0); }

    public double getMood() { return mood; }
    public void setMood(double v) {
        double maxAllowed = getMaxAllowedMood();
        this.mood = Mth.clamp(v, 0.0, maxAllowed);
    }

    public double getMaxAllowedMood() {
        if (hasActiveInjury() || this.poorPrognosis || hasAilment(AILMENT_POOR_PROGNOSIS)) {
            return 20.0; // Injuries / Poor Prognosis drop and lock Mood to Awful (<= 20)
        }
        if (hasAnyAilment()) {
            return 60.0; // Ailments cap Mood at Normal (<= 60)
        }
        return 100.0;
    }

    public void enforceMoodCap() {
        double maxAllowed = getMaxAllowedMood();
        if (this.mood > maxAllowed) {
            this.mood = maxAllowed;
        }
    }

    public void enforceHealthMoodAndFatigueRules() {
        if (this.fatigue >= 80.0) {
            if (!hasAilment(AILMENT_UNSTABLE_LEGS)) {
                this.activeAilments.add(AILMENT_UNSTABLE_LEGS);
            }
        } else {
            this.activeAilments.removeIf(a -> AILMENT_UNSTABLE_LEGS.equalsIgnoreCase(a));
        }
        if (INJURY_CATASTROPHIC_FRACTURE.equalsIgnoreCase(this.activeInjury)) {
            this.poorPrognosis = true;
        }
        if (this.poorPrognosis) {
            if (!hasAilment(AILMENT_POOR_PROGNOSIS)) {
                this.activeAilments.add(AILMENT_POOR_PROGNOSIS);
            }
            if (!hasAilment(AILMENT_LAMENESS)) {
                this.activeAilments.add(AILMENT_LAMENESS);
            }
        }
        if (hasActiveInjury() && !hasAilment(AILMENT_LAMENESS)) {
            this.activeAilments.add(AILMENT_LAMENESS);
        }
        enforceMoodCap();
    }

    // =========================================================================
    // STEP 17: AILMENT & INJURY ACCESSORS & HELPERS
    // =========================================================================
    public List<String> getActiveAilments() {
        return this.activeAilments;
    }

    public boolean hasAnyAilment() {
        return !this.activeAilments.isEmpty();
    }

    public boolean hasAilment(String ailment) {
        if (ailment == null || ailment.isBlank()) return false;
        for (String a : this.activeAilments) {
            if (ailment.equalsIgnoreCase(a)) return true;
        }
        return false;
    }

    public boolean addAilment(String ailment) {
        if (ailment == null || ailment.isBlank()) return false;
        if (AILMENT_POOR_PROGNOSIS.equalsIgnoreCase(ailment)) {
            this.poorPrognosis = true;
            if (!hasAilment(AILMENT_LAMENESS)) {
                this.activeAilments.add(AILMENT_LAMENESS);
            }
        }
        if (AILMENT_OVERWEIGHT.equalsIgnoreCase(ailment)) {
            this.overweightExerciseMeters = 0.0;
            if (this.hunger < 100.0) {
                this.hunger = 100.0;
            }
        }
        if (!hasAilment(ailment)) {
            this.activeAilments.add(ailment);
            enforceMoodCap();
            return true;
        }
        enforceMoodCap();
        return false;
    }

    public boolean removeAilment(String ailment) {
        if (ailment == null || ailment.isBlank()) return false;
        boolean removed = this.activeAilments.removeIf(a -> ailment.equalsIgnoreCase(a));
        if (AILMENT_POOR_PROGNOSIS.equalsIgnoreCase(ailment)) {
            this.poorPrognosis = false;
            this.poorPrognosisAppleImmunity = false;
            this.poorPrognosisGoldenApplesUsed = 0;
            this.poorPrognosisGoldenAppleActive = false;
            this.poorPrognosisGoldenAppleDeathChance = 49;
            this.triedToSaveWithApples = false;
        }
        if (AILMENT_OVERWEIGHT.equalsIgnoreCase(ailment)) {
            this.overweightExerciseMeters = 0.0;
            this.overfedStreak = 0;
        }
        return removed;
    }

    public void clearAllAilments() {
        this.activeAilments.clear();
        this.overweightExerciseMeters = 0.0;
        this.overfedStreak = 0;
        this.poorPrognosis = false;
        this.poorPrognosisAppleImmunity = false;
        this.poorPrognosisGoldenApplesUsed = 0;
        this.poorPrognosisGoldenAppleActive = false;
        this.poorPrognosisGoldenAppleDeathChance = 49;
        this.triedToSaveWithApples = false;
        if (this.fatigue >= 80.0) {
            this.activeAilments.add(AILMENT_UNSTABLE_LEGS);
        }
    }

    /**
     * Laryngeal Hemiplegia and Atrial Fibrillation go away on their own when a race ends.
     */
    public boolean clearRaceEndAilments() {
        boolean r1 = removeAilment(AILMENT_LARYNGEAL);
        boolean r2 = removeAilment(AILMENT_AFIB);
        return r1 || r2;
    }

    public boolean hasActiveInjury() {
        return this.activeInjury != null && !this.activeInjury.isBlank();
    }

    public String getActiveInjury() {
        return this.activeInjury != null ? this.activeInjury : INJURY_NONE;
    }

    public double getInjuryDaysRemaining() {
        return this.injuryDaysRemaining;
    }

    public void setInjuryDaysRemaining(double days) {
        this.injuryDaysRemaining = Math.max(0.0, days);
    }

    public boolean isVetTreatedThisInjury() {
        return this.vetTreatedThisInjury;
    }

    public void setVetTreatedThisInjury(boolean v) {
        this.vetTreatedThisInjury = v;
    }

    public void inflictInjury(String injuryType, double baseRecoveryDays) {
        if (injuryType == null || injuryType.isBlank()) {
            clearInjury();
            return;
        }
        this.activeInjury = injuryType;
        this.injuryDaysRemaining = Math.max(0.5, baseRecoveryDays);
        this.vetTreatedThisInjury = false;
        addAilment(AILMENT_LAMENESS);
        if (INJURY_CATASTROPHIC_FRACTURE.equalsIgnoreCase(injuryType)) {
            this.poorPrognosis = true;
            this.injuryDaysRemaining = 999.0;
            addAilment(AILMENT_POOR_PROGNOSIS);
        }
        // Injuries immediately drop Mood down to Awful (<= 20.0)
        this.mood = Math.min(this.mood, 18.0);
        enforceMoodCap();
    }

    public void clearInjury() {
        this.activeInjury = INJURY_NONE;
        this.injuryDaysRemaining = 0.0;
        this.vetTreatedThisInjury = false;
        this.poorPrognosis = false;
        this.poorPrognosisAppleImmunity = false;
        this.poorPrognosisGoldenApplesUsed = 0;
        this.poorPrognosisGoldenAppleActive = false;
        this.poorPrognosisGoldenAppleDeathChance = 49;
        this.triedToSaveWithApples = false;
        removeAilment(AILMENT_POOR_PROGNOSIS);
        removeAilment(AILMENT_LAMENESS);
        removeAilment(AILMENT_LAMINITIS);
    }

    public boolean hasPoorPrognosis() {
        return this.poorPrognosis || hasAilment(AILMENT_POOR_PROGNOSIS)
                || INJURY_CATASTROPHIC_FRACTURE.equalsIgnoreCase(this.activeInjury);
    }

    public void setPoorPrognosis(boolean v) {
        this.poorPrognosis = v;
        if (v) {
            addAilment(AILMENT_POOR_PROGNOSIS);
            addAilment(AILMENT_LAMENESS);
            this.mood = Math.min(this.mood, 15.0);
        } else {
            removeAilment(AILMENT_POOR_PROGNOSIS);
            this.poorPrognosisAppleImmunity = false;
            this.poorPrognosisGoldenApplesUsed = 0;
            this.poorPrognosisGoldenAppleActive = false;
            this.poorPrognosisGoldenAppleDeathChance = 49;
            this.triedToSaveWithApples = false;
        }
        enforceMoodCap();
    }

    public boolean hasPoorPrognosisAppleImmunity() {
        return this.poorPrognosisAppleImmunity;
    }

    public void setPoorPrognosisAppleImmunity(boolean v) {
        this.poorPrognosisAppleImmunity = v;
    }

    public int getPoorPrognosisGoldenApplesUsed() {
        return this.poorPrognosisGoldenApplesUsed;
    }

    public void setPoorPrognosisGoldenApplesUsed(int count) {
        this.poorPrognosisGoldenApplesUsed = Math.max(0, count);
    }

    public int getNextGoldenAppleDeathChancePercent() {
        return Math.min(49, this.poorPrognosisGoldenApplesUsed * 5);
    }

    public boolean isPoorPrognosisGoldenAppleActive() {
        return this.poorPrognosisGoldenAppleActive;
    }

    public void setPoorPrognosisGoldenAppleActive(boolean active) {
        this.poorPrognosisGoldenAppleActive = active;
    }

    public int getPoorPrognosisGoldenAppleDeathChance() {
        return Mth.clamp(this.poorPrognosisGoldenAppleDeathChance, 0, 49);
    }

    public void setPoorPrognosisGoldenAppleDeathChance(int chance) {
        this.poorPrognosisGoldenAppleDeathChance = Mth.clamp(chance, 0, 49);
    }

    public boolean hasTriedToSaveWithApples() {
        return this.triedToSaveWithApples;
    }

    public void setTriedToSaveWithApples(boolean v) {
        this.triedToSaveWithApples = v;
    }

    public boolean isCareerEnded() {
        return this.careerEnded;
    }

    public void setCareerEnded(boolean v) {
        this.careerEnded = v;
    }

    public long getLastCheckedMinecraftDay() {
        return this.lastCheckedMinecraftDay;
    }

    public void setLastCheckedMinecraftDay(long day) {
        this.lastCheckedMinecraftDay = day;
    }

    public long getLastCheckedDeathsDoorRealDay() {
        return this.lastCheckedDeathsDoorRealDay;
    }

    public void setLastCheckedDeathsDoorRealDay(long realDay) {
        this.lastCheckedDeathsDoorRealDay = realDay;
    }

    public boolean canBeMounted() {
        return !this.foal && !this.yearling && !this.deathsDoor && !hasPoorPrognosis() && !hasActiveInjury() && !hasAilment(AILMENT_LAMENESS);
    }

    public boolean refusesSolidFood() {
        return hasAilment(AILMENT_FEVER) || hasAilment(AILMENT_COLIC);
    }

    public String getFormattedAilmentsSummary() {
        if (this.activeAilments.isEmpty()) {
            return "§a" + tr("None (Healthy)", "無 (健康)", "无 (健康)", "なし (健康)");
        }
        List<String> colored = new ArrayList<>();
        for (String a : this.activeAilments) {
            if (AILMENT_POOR_PROGNOSIS.equalsIgnoreCase(a)) {
                continue;
            }
            String locA = localizeAilment(a);
            if (AILMENT_LAMINITIS.equalsIgnoreCase(a)) {
                colored.add("§4§l" + locA);
            } else if (AILMENT_UNSTABLE_LEGS.equalsIgnoreCase(a) || AILMENT_AFIB.equalsIgnoreCase(a) || AILMENT_LAMENESS.equalsIgnoreCase(a)) {
                colored.add("§c" + locA);
            } else {
                colored.add("§6" + locA);
            }
        }
        if (colored.isEmpty()) {
            return "§a" + tr("None (Healthy)", "無 (健康)", "无 (健康)", "なし (健康)");
        }
        return String.join("§7, ", colored);
    }

    public String getFormattedInjurySummary() {
        if (!hasActiveInjury()) {
            return "§a" + tr("None", "無", "无", "なし");
        }
        String locInj = localizeInjury(this.activeInjury);
        if (INJURY_CATASTROPHIC_FRACTURE.equalsIgnoreCase(this.activeInjury) || hasPoorPrognosis()) {
            return "§4§l" + locInj;
        }
        return String.format(Locale.ROOT, "§c%s §7(%.1f %s%s)",
                locInj, this.injuryDaysRemaining,
                tr("MC days left", "MC天剩餘", "MC天剩余", "MC日残り"),
                this.vetTreatedThisInjury ? ", §a" + tr("Vet Treated", "獸醫已診治", "兽医已诊治", "獣医治療済") : "");
    }

    public String getCompactHudConditionTag() {
        if (this.deathsDoor) {
            return "§6§l" + tr("[DEATH'S DOOR]", "[瀕死邊緣]", "[濒死边缘]", "[危篤状態]");
        }
        if (hasPoorPrognosis()) {
            return "§4§l" + tr("[POOR PROGNOSIS]", "[預後不良]", "[预后不良]", "[予後不良]");
        }
        if (hasActiveInjury()) {
            return "§c§l[" + localizeInjury(this.activeInjury).toUpperCase(Locale.ROOT) + "]";
        }
        if (hasAilment(AILMENT_AFIB)) {
            return "§4§l" + tr("[A-FIB!]", "[心房顫動!]", "[心房颤动!]", "[心房細動!]");
        }
        if (hasAilment(AILMENT_LARYNGEAL)) {
            return "§c§l" + tr("[LARYNGEAL 2x STM]", "[喘鳴症 2倍耗耐]", "[喘鸣症 2倍耗耐]", "[喘鳴症 消費2倍]");
        }
        if (hasAilment(AILMENT_LAMENESS)) {
            return "§c§l" + tr("[LAME: 12 KM/H CAP]", "[跛行: 限速12km/h]", "[跛行: 限速12km/h]", "[跛行: 制限12km/h]");
        }
        if (hasAilment(AILMENT_FEVER)) {
            return "§6§l" + tr("[FEVER]", "[發燒]", "[发烧]", "[発熱]");
        }
        if (hasAilment(AILMENT_EIPH)) {
            return "§c" + tr("[BLEEDER/EIPH]", "[肺出血/EIPH]", "[肺出血/EIPH]", "[鼻出血/EIPH]");
        }
        if (hasAilment(AILMENT_COLIC)) {
            return "§6" + tr("[COLIC]", "[腹痛/絞痛]", "[腹痛/绞痛]", "[疝痛]");
        }
        if (hasAilment(AILMENT_OVERWEIGHT)) {
            return "§6" + tr("[OVERWEIGHT]", "[體重過重]", "[体重过重]", "[太り気味]");
        }
        if (hasAilment(AILMENT_UNSTABLE_LEGS)) {
            return "§c" + tr("[UNSTABLE LEGS]", "[雙腿發軟]", "[双腿发软]", "[脚元不安]");
        }
        if (this.careerEnded) {
            return "§7" + tr("[RETIRED FROM INJURY]", "[因傷退役]", "[因伤退役]", "[怪我引退]");
        }
        if (this.retired) {
            if ("Female".equalsIgnoreCase(this.gender)) {
                if (this.pregnant) return "§d§l" + tr("[BROODMARE: PREGNANT]", "[繁殖牝馬: 懷孕中]", "[繁殖母马: 怀孕中]", "[繁殖牝馬: 受胎中]");
                if (this.inHeat) return "§d§l" + tr("[BROODMARE: IN HEAT]", "[繁殖牝馬: 發情期]", "[繁殖母马: 发情期]", "[繁殖牝馬: 発情期]");
                return "§d" + tr("[RETIRED BROODMARE]", "[退役繁殖牝馬]", "[退役繁殖母马]", "[引退繁殖牝馬]");
            }
            return "§b" + tr("[RETIRED STUD]", "[退役種馬]", "[退役种马]", "[引退種牡馬]");
        }
        return "";
    }

    public boolean hasWeightedTack() { return weightedTack; }
    public void setWeightedTack(boolean v) { this.weightedTack = v; }

    public boolean hasBlinkersHood() { return blinkersHood; }
    public void setBlinkersHood(boolean v) { this.blinkersHood = v; }

    public int getTurfAptitude() { return turfAptitude; }
    public void setTurfAptitude(int v) { this.turfAptitude = Mth.clamp(v, 0, 10); }
    public int getDirtAptitude() { return dirtAptitude; }
    public void setDirtAptitude(int v) { this.dirtAptitude = Mth.clamp(v, 0, 10); }

    public int getMinDistance() { return minDistance; }
    public void setMinDistance(int v) { this.minDistance = Mth.clamp((v / 100) * 100, 1000, 4500); }
    public int getMaxDistance() { return maxDistance; }
    public void setMaxDistance(int v) { this.maxDistance = Mth.clamp((v / 100) * 100, 1000, 4500); }

    public void setName(String newName) {
        if (newName != null && !newName.isBlank()) {
            this.name = newName.trim();
            this.registered = true;
        }
    }

    // =========================================================================
    // STEP 18: RETIREMENT, MARE HEAT, PREGNANCY, FOAL GROWTH, 100 HP & PEDIGREE
    // =========================================================================
    public boolean isRetired() {
        return this.retired || this.careerEnded;
    }

    public void setRetired(boolean retired) {
        this.retired = retired;
    }

    public boolean isInHeat() {
        return "Female".equalsIgnoreCase(this.gender) && isRetired() && !this.foal && !this.yearling && !this.pregnant && this.inHeat;
    }

    public void setInHeat(boolean inHeat) {
        this.inHeat = inHeat;
    }

    public boolean isManualHeatOverride() {
        return this.manualHeatOverride;
    }

    public void setManualHeatOverride(boolean manualOverride) {
        this.manualHeatOverride = manualOverride;
    }

    public boolean isPregnant() {
        return "Female".equalsIgnoreCase(this.gender) && this.pregnant;
    }

    public void setPregnant(boolean pregnant) {
        this.pregnant = pregnant;
    }

    public long getPregnancyConceptionMillis() {
        return this.pregnancyConceptionMillis;
    }

    public long getPregnancyDurationMillis() {
        return this.pregnancyDurationMillis;
    }

    public void setPregnancyDurationMillis(long durationMillis) {
        this.pregnancyDurationMillis = Math.max(1000L, durationMillis);
    }

    public void setPregnancyConceptionMillis(long conceptionMillis) {
        this.pregnancyConceptionMillis = conceptionMillis;
    }

    public String getPregnantSireName() {
        return this.pregnantSireName != null ? this.pregnantSireName : "";
    }

    public String getPregnantSireUUID() {
        return this.pregnantSireUUID != null ? this.pregnantSireUUID : "";
    }

    public CompoundTag getPregnantSireNbt() {
        return this.pregnantSireNbt;
    }

    public void startPregnancy(Horse sireEntity, ThoroughbredData sireData, long gestationDurationMillis) {
        this.pregnant = true;
        this.inHeat = false;
        this.pregnancyConceptionMillis = System.currentTimeMillis();
        this.pregnancyDurationMillis = Math.max(60_000L, gestationDurationMillis);
        this.pregnantSireName = sireData != null ? sireData.getName() : "Unknown Sire";
        this.pregnantSireUUID = sireEntity != null ? sireEntity.getUUID().toString() : "";
        this.pregnantSireNbt = sireData != null ? sireData.toNBT() : new CompoundTag();
    }

    public void clearPregnancy() {
        this.pregnant = false;
        this.pregnancyConceptionMillis = 0L;
        this.pregnancyDurationMillis = 0L;
        this.pregnantSireName = "";
        this.pregnantSireUUID = "";
        this.pregnantSireNbt = new CompoundTag();
    }

    public double getPregnancyProgressRatio() {
        if (!this.pregnant || this.pregnancyDurationMillis <= 0L) return 0.0;
        long elapsed = Math.max(0L, System.currentTimeMillis() - this.pregnancyConceptionMillis);
        return Mth.clamp((double) elapsed / this.pregnancyDurationMillis, 0.0, 1.0);
    }

    public double getPregnancyDaysRemaining() {
        if (!this.pregnant || this.pregnancyDurationMillis <= 0L) return 0.0;
        long remMillis = Math.max(0L, (this.pregnancyConceptionMillis + this.pregnancyDurationMillis) - System.currentTimeMillis());
        return remMillis / 86_400_000.0;
    }

    public boolean isReadyToGiveBirth() {
        return this.pregnant && this.pregnancyDurationMillis > 0L
                && (System.currentTimeMillis() - this.pregnancyConceptionMillis >= this.pregnancyDurationMillis);
    }

    public boolean isFoal() {
        return this.foal;
    }

    public void setFoal(boolean foal) {
        this.foal = foal;
    }

    public boolean isYearling() {
        return !this.foal && this.yearling;
    }

    public void setYearling(boolean yearling) {
        this.yearling = yearling;
    }

    public boolean isUnderTwoEquineYears() {
        return this.foal || this.yearling;
    }

    public void advanceFoalToYearling(long oneEquineYearDurationMillis) {
        this.foal = false;
        this.yearling = true;
        this.yearlingStartMillis = System.currentTimeMillis();
        this.yearlingGrowDurationMillis = Math.max(60_000L, oneEquineYearDurationMillis);
        this.cachedEquineAgeYears = 1;
    }

    public double getYearlingGrowthProgressRatio() {
        if (!this.yearling || this.yearlingGrowDurationMillis <= 0L) return 1.0;
        long elapsed = Math.max(0L, System.currentTimeMillis() - this.yearlingStartMillis);
        return Mth.clamp((double) elapsed / this.yearlingGrowDurationMillis, 0.0, 1.0);
    }

    public double getYearlingGrowthDaysRemaining() {
        if (!this.yearling || this.yearlingGrowDurationMillis <= 0L) return 0.0;
        long remMillis = Math.max(0L, (this.yearlingStartMillis + this.yearlingGrowDurationMillis) - System.currentTimeMillis());
        return remMillis / 86_400_000.0;
    }

    public boolean isYearlingReadyToMature() {
        return this.yearling && this.yearlingGrowDurationMillis > 0L
                && (System.currentTimeMillis() - this.yearlingStartMillis >= this.yearlingGrowDurationMillis);
    }

    public boolean isBredFoalPendingName() {
        return this.bredFoalPendingName;
    }

    public void setBredFoalPendingName(boolean pending) {
        this.bredFoalPendingName = pending;
    }

    public long getBirthTimestampMillis() {
        return this.birthTimestampMillis;
    }

    public long getFoalGrowDurationMillis() {
        return this.foalGrowDurationMillis;
    }

    public void startFoalGrowth(long growDurationMillis) {
        this.foal = true;
        this.yearling = false;
        this.birthTimestampMillis = System.currentTimeMillis();
        this.foalGrowDurationMillis = Math.max(60_000L, growDurationMillis);
        this.yearlingGrowDurationMillis = Math.max(60_000L, growDurationMillis);
    }

    public double getFoalGrowthProgressRatio() {
        if (!this.foal || this.foalGrowDurationMillis <= 0L) return 1.0;
        long elapsed = Math.max(0L, System.currentTimeMillis() - this.birthTimestampMillis);
        return Mth.clamp((double) elapsed / this.foalGrowDurationMillis, 0.0, 1.0);
    }

    public double getFoalGrowthDaysRemaining() {
        if (!this.foal || this.foalGrowDurationMillis <= 0L) return 0.0;
        long remMillis = Math.max(0L, (this.birthTimestampMillis + this.foalGrowDurationMillis) - System.currentTimeMillis());
        return remMillis / 86_400_000.0;
    }

    public boolean isFoalReadyToGrowUp() {
        return this.foal && this.foalGrowDurationMillis > 0L
                && (System.currentTimeMillis() - this.birthTimestampMillis >= this.foalGrowDurationMillis);
    }

    public boolean isPureWhiteCoat() {
        return this.pureWhiteCoat;
    }

    public void setPureWhiteCoat(boolean pureWhite) {
        this.pureWhiteCoat = pureWhite;
    }

    public int getHereditaryPoints() {
        return this.hereditaryPoints;
    }

    public void setHereditaryPoints(int hp) {
        this.hereditaryPoints = Mth.clamp(hp, 0, 100);
        initGeneSlotsFromHP(this.hereditaryPoints, RandomSource.create());
    }

    public byte[] getGeneSlots() {
        return this.geneSlots;
    }

    public boolean isAtavismTriggered() {
        return this.atavismTriggered;
    }

    public String getFormattedHereditaryPoints() {
        int hp = getHereditaryPoints();
        String tier;
        if (hp >= 85) tier = "§6§l" + tr("[Epochal / Legend]", "[跨時代傳奇]", "[跨时代传奇]", "[歴史的名馬級]");
        else if (hp >= 72) tier = "§e§l" + tr("[G1 Elite]", "[G1 頂級名駒]", "[G1 顶级名驹]", "[G1級エリート]");
        else if (hp >= 58) tier = "§a" + tr("[Graded Caliber]", "[分級賽級別]", "[分级赛级别]", "[重賞級]");
        else if (hp >= 42) tier = "§f" + tr("[Standard]", "[標準級別]", "[标准级别]", "[標準]");
        else tier = "§7" + tr("[Modest]", "[普通血統]", "[普通血统]", "[一般]");
        return "§f" + hp + "% " + tr("Potential", "遺傳潛力", "遗传潜力", "潜在能力") + " §7(100/100 HP) " + tier;
    }

    public String getPedigreeAncestor(int index) {
        if (index < 0 || index >= PEDIGREE_SIZE) return "Unknown";
        String val = this.pedigreeTree[index];
        return (val == null || val.isEmpty()) ? "Unknown" : val;
    }

    public int getPedigreeAncestorHP(int index) {
        if (index < 0 || index >= PEDIGREE_SIZE) return 50;
        return this.pedigreeHP[index];
    }

    public String getInbreedingStatus() {
        return getLocalizedInbreedingSummary();
    }

    public String getFormattedBreedingStatus() {
        return getFormattedLifeStageAndBreedingStatus();
    }

    public String getInbreedingSummary() {
        return getLocalizedInbreedingSummary();
    }

    public String getRawInbreedingSummary() {
        return this.inbreedingSummary != null ? this.inbreedingSummary : "Outcross";
    }

    public String getLocalizedInbreedingStatus() {
        return getLocalizedInbreedingSummary();
    }

    public String getLocalizedInbreedingSummary() {
        String raw = this.inbreedingSummary != null ? this.inbreedingSummary.trim() : "";
        if (raw.isEmpty() || raw.equalsIgnoreCase("Outcross")) {
            return tr("Outcross", "異系交配 (Outcross)", "异系交配 (Outcross)", "アウトブリード");
        }
        if (raw.equalsIgnoreCase("Foundation (Outcross)")) {
            return tr("Foundation (Outcross)", "基礎名驥 (異系)", "基础名骥 (异系)", "基礎馬 (アウトブリード)");
        }
        if (raw.equalsIgnoreCase("Outcross (No Inbreeding)")) {
            return tr("Outcross (No Inbreeding)", "異系交配 (無近親交配)", "异系交配 (无近亲交配)", "アウトブリード (近親交配なし)");
        }
        String clean = raw.replaceAll("§[0-9a-fk-or]", "").trim();
        if (clean.startsWith("Close Inbreeding:")) {
            int pOpen = clean.lastIndexOf('(');
            int pClose = clean.lastIndexOf(')');
            if (pOpen > 0 && pClose > pOpen) {
                String anc = clean.substring("Close Inbreeding:".length(), pOpen).trim();
                String gen = clean.substring(pOpen + 1, pClose).trim();
                String[] parts = gen.split("x");
                if (parts.length == 2) {
                    try {
                        int p = Integer.parseInt(parts[0].trim());
                        int m = Integer.parseInt(parts[1].trim());
                        return "§c" + String.format(Locale.ROOT, tr("Close Inbreeding: %s (%dx%d)", "近親交配: %s (%dx%d)", "近亲交配: %s (%dx%d)", "インブリード: %s (%dx%d)"), anc, p, m);
                    } catch (Exception ignored) {}
                }
            }
            return "§c" + clean;
        }
        if (clean.startsWith("Linebred Cross:")) {
            int pOpen = clean.lastIndexOf('(');
            int pClose = clean.lastIndexOf(')');
            if (pOpen > 0 && pClose > pOpen) {
                String anc = clean.substring("Linebred Cross:".length(), pOpen).trim();
                String gen = clean.substring(pOpen + 1, pClose).trim();
                String[] parts = gen.split("x");
                if (parts.length == 2) {
                    try {
                        int p = Integer.parseInt(parts[0].trim());
                        int m = Integer.parseInt(parts[1].trim());
                        return "§e" + String.format(Locale.ROOT, tr("Linebred Cross: %s (%dx%d)", "血線交配: %s (%dx%d)", "血线交配: %s (%dx%d)", "ラインブリード: %s (%dx%d)"), anc, p, m);
                    } catch (Exception ignored) {}
                }
            }
            return "§e" + clean;
        }
        return raw;
    }

    public boolean isInbredDepressionTriggered() {
        return this.inbredDepressionTriggered;
    }

    public String[] getPedigreeTree() {
        return this.pedigreeTree;
    }

    public int[] getPedigreeHP() {
        return this.pedigreeHP;
    }

    public String getSireName() {
        return (this.pedigreeTree[0] != null && !this.pedigreeTree[0].isBlank()) ? this.pedigreeTree[0] : "Unknown (Wild)";
    }

    public String getDamName() {
        return (this.pedigreeTree[1] != null && !this.pedigreeTree[1].isBlank()) ? this.pedigreeTree[1] : "Unknown (Wild)";
    }

    public List<String> getGeneticTraits() {
        return this.geneticTraits;
    }

    public boolean hasGeneticTrait(String traitName) {
        if (traitName == null || traitName.isBlank()) return false;
        for (String tr : this.geneticTraits) {
            if (traitName.equalsIgnoreCase(tr)) return true;
        }
        return false;
    }

    /**
     * Applies a genetic trait and enforces birth/stat-cap rules for Small Hooves & Large Hooves.
     */
    public boolean applyTraitAtBirth(String traitName) {
        if (traitName == null || traitName.isBlank()) return false;
        if (TRAIT_SMALL_HOOVES.equalsIgnoreCase(traitName)) {
            this.geneticTraits.removeIf(t -> TRAIT_LARGE_HOOVES.equalsIgnoreCase(t));
            if (!hasGeneticTrait(TRAIT_SMALL_HOOVES)) {
                this.geneticTraits.add(TRAIT_SMALL_HOOVES);
                // Small Hooves: Higher Speed Cap (+60 internal / +6.0 display), and initial Power is permanently halved at birth!
                this.speedMax = Math.min(1100, this.speedMax + 60);
                this.powerMax = Math.max(100, this.powerMax / 2);
                this.powerCurrent = Math.max(50, this.powerCurrent / 2);
                return true;
            }
            return false;
        }
        if (TRAIT_LARGE_HOOVES.equalsIgnoreCase(traitName)) {
            this.geneticTraits.removeIf(t -> TRAIT_SMALL_HOOVES.equalsIgnoreCase(t));
            if (!hasGeneticTrait(TRAIT_LARGE_HOOVES)) {
                this.geneticTraits.add(TRAIT_LARGE_HOOVES);
                // Large Hooves: Doubled Power Cap!
                this.powerMax = Math.min(2000, this.powerMax * 2);
                return true;
            }
            return false;
        }
        if (TRAIT_X_FACTOR.equalsIgnoreCase(traitName)) {
            if (this.xFactorCopies <= 0) {
                this.xFactorCopies = 1;
            }
        }
        if (!hasGeneticTrait(traitName)) {
            this.geneticTraits.add(traitName);
            return true;
        }
        return false;
    }

    public boolean addGeneticTrait(String traitName) {
        return applyTraitAtBirth(traitName);
    }

    public boolean removeGeneticTrait(String traitName) {
        if (traitName == null || traitName.isBlank()) return false;
        boolean rem = this.geneticTraits.removeIf(t -> traitName.equalsIgnoreCase(t));
        if (rem && TRAIT_LARGE_HOOVES.equalsIgnoreCase(traitName)) {
            this.powerMax = Math.min(1000, this.powerMax);
            this.powerCurrent = Math.min(this.powerMax, this.powerCurrent);
        }
        if (TRAIT_X_FACTOR.equalsIgnoreCase(traitName)) {
            this.xFactorCopies = 0;
        }
        return rem;
    }

    public void clearGeneticTraits() {
        this.geneticTraits.clear();
        this.xFactorCopies = 0;
        this.powerMax = Math.min(1000, this.powerMax);
        this.powerCurrent = Math.min(this.powerMax, this.powerCurrent);
    }

    public int getXFactorCopies() {
        if (hasGeneticTrait(TRAIT_X_FACTOR) && this.xFactorCopies <= 0) {
            return 1;
        }
        return this.xFactorCopies;
    }

    public void setXFactorCopies(int copies) {
        int maxCopies = "Female".equalsIgnoreCase(this.gender) ? 2 : 1;
        this.xFactorCopies = Mth.clamp(copies, 0, maxCopies);
        if (this.xFactorCopies <= 0) {
            this.geneticTraits.removeIf(t -> TRAIT_X_FACTOR.equalsIgnoreCase(t));
        }
    }

    public boolean hasXFactorGene() {
        return getXFactorCopies() > 0 || hasGeneticTrait(TRAIT_X_FACTOR);
    }

    public String getFormattedTraitsSummary() {
        if (this.geneticTraits.isEmpty()) {
            return "§7" + tr("None (Standard)", "無 (標準)", "无 (标准)", "なし (標準)");
        }
        List<String> out = new ArrayList<>();
        for (String tr : this.geneticTraits) {
            String locTr = localizeTrait(tr);
            if (TRAIT_X_FACTOR.equalsIgnoreCase(tr)) {
                out.add("§d§l" + locTr);
            } else if (TRAIT_SMALL_HOOVES.equalsIgnoreCase(tr)
                    || TRAIT_LARGE_HOOVES.equalsIgnoreCase(tr)
                    || TRAIT_SPRINTER_TWITCH.equalsIgnoreCase(tr)
                    || TRAIT_DIESEL_STAYER.equalsIgnoreCase(tr)) {
                out.add("§b§l" + locTr);
            } else if (TRAIT_GLASS_LEGS.equalsIgnoreCase(tr)
                    || TRAIT_ROARER_GENE.equalsIgnoreCase(tr)
                    || TRAIT_WEAK_CONSTITUTION.equalsIgnoreCase(tr)
                    || TRAIT_HIGH_STRUNG.equalsIgnoreCase(tr)
                    || TRAIT_SOFT_PALATE.equalsIgnoreCase(tr)
                    || TRAIT_BRITTLE_HOOVES.equalsIgnoreCase(tr)) {
                out.add("§c" + locTr);
            } else {
                out.add("§a" + locTr);
            }
        }
        return String.join("§7, ", out);
    }

    public String getFormattedLifeStageAndBreedingStatus() {
        if (this.foal) {
            double pct = getFoalGrowthProgressRatio() * 100.0;
            return String.format(Locale.ROOT,
                    "§e§l%s (0 -> 1: %.0f%% | %.1fd)",
                    tr("Foal", "幼駒 (0歲)", "幼驹 (0岁)", "当歳馬 (0歳)"),
                    pct, getFoalGrowthDaysRemaining());
        }
        if (this.yearling) {
            double pct = getYearlingGrowthProgressRatio() * 100.0;
            return String.format(Locale.ROOT,
                    "§6§l%s (1 -> 2: %.0f%% | %.1fd)",
                    tr("Yearling", "一歲馬 (1歲)", "一岁马 (1岁)", "1歳馬 (イヤリング)"),
                    pct, getYearlingGrowthDaysRemaining());
        }
        int ageYrs = getEquineAgeYears();
        String ageSuffix = " §7(" + ageYrs + tr("yo", "歲", "岁", "歳") + ")";
        if (this.deathsDoor) {
            return "§6§l" + tr("AT DEATH'S DOOR", "瀕死邊緣", "濒死边缘", "危篤状態") + ageSuffix
                    + " §e(" + tr("Visit with High Bond", "高羈絆探望可撫慰", "高羁绊探望可抚慰", "高絆で見舞い可") + ")";
        }
        if (hasPoorPrognosis()) {
            return "§4§l" + tr("POOR PROGNOSIS", "預後不良", "预后不良", "予後不良") + ageSuffix;
        }
        if (this.pregnant) {
            double pct = getPregnancyProgressRatio() * 100.0;
            return String.format(Locale.ROOT, "§d§l%s%s — %s %s (%.0f%% | %.1fd)",
                    tr("Broodmare", "繁殖牝馬", "繁殖母马", "繁殖牝馬"),
                    ageSuffix,
                    tr("Pregnant by", "懷有種馬", "怀有种马", "受胎相手:"),
                    getPregnantSireName(), pct, getPregnancyDaysRemaining());
        }
        if (isGelding()) {
            return isRetired()
                    ? "§3§l" + tr("Retired Gelding", "退役閹馬", "退役阉马", "引退セン馬") + ageSuffix
                    : "§3" + tr("Active Gelding", "現役閹馬", "现役阉马", "現役セン馬") + ageSuffix;
        }
        if (isRetired()) {
            if ("Female".equalsIgnoreCase(this.gender)) {
                return this.inHeat
                        ? "§d§l" + tr("Retired Broodmare", "退役繁殖牝馬", "退役繁殖母马", "引退繁殖牝馬") + ageSuffix + " §a§l" + tr("[IN HEAT]", "[發情期]", "[发情期]", "[発情期]")
                        : "§d" + tr("Retired Broodmare", "退役繁殖牝馬", "退役繁殖母马", "引退繁殖牝馬") + ageSuffix + " §7" + tr("(Out of Heat)", "(非發情期)", "(非发情期)", "(発情期外)");
            } else {
                return "§b§l" + tr("Retired Stud Stallion", "退役種馬", "退役种马", "引退種牡馬") + ageSuffix + " §a" + tr("[Available]", "[可配種]", "[可配种]", "[種付可]");
            }
        }
        return "§a" + tr("Active Racehorse", "現役賽駒", "现役赛驹", "現役競走馬") + ageSuffix;
    }

    private void initGeneSlotsFromHP(int targetHp, RandomSource random) {
        int clamped = Mth.clamp(targetHp, 0, TOTAL_HP_SLOTS);
        for (int i = 0; i < TOTAL_HP_SLOTS; i++) {
            this.geneSlots[i] = 0;
        }
        List<Integer> indices = new ArrayList<>(TOTAL_HP_SLOTS);
        for (int i = 0; i < TOTAL_HP_SLOTS; i++) {
            indices.add(i);
        }
        for (int i = 0; i < clamped && !indices.isEmpty(); i++) {
            int pickIdx = random.nextInt(indices.size());
            int slot = indices.remove(pickIdx);
            this.geneSlots[slot] = 1;
        }
    }

    /**
     * Generates a bred foal's complete 4-Generation Pedigree Tree, 100 Hereditary Points (HP)
     * across 4 ancestral generations (50% Parents, 25% Grandparents, 12.5% Great-Grandparents,
     * 6.25% Great-Great-Grandparents, 6.25% Wild Variance), Generation-Skipping (Atavism),
     * Inbreeding/Linebreeding concentration vs. depression, Genetic Traits (including Small/Large Hooves),
     * and True Pure-White Coat inheritance!
     */
    public void generateBredFoalGenetics(RandomSource random, String tempFoalName, String chosenGender,
                                         ThoroughbredData sire, ThoroughbredData dam, long foalGrowDurationMillis) {
        this.registered = true;
        this.name = tempFoalName;
        this.gender = chosenGender;
        this.foal = true;
        this.yearling = false;
        this.bredFoalPendingName = true;
        this.baseAdultEquineAgeYears = 2;
        this.adultStartTimestampMillis = 0L;
        this.cachedEquineAgeYears = 0;
        this.deathsDoor = false;
        this.birthTimestampMillis = System.currentTimeMillis();
        this.foalGrowDurationMillis = Math.max(60_000L, foalGrowDurationMillis);
        this.yearlingGrowDurationMillis = Math.max(60_000L, foalGrowDurationMillis);
        this.retired = false;
        this.inHeat = false;
        this.pregnant = false;

        // 1. Build 4-Generation Pedigree Tree (30 Ancestors) & Ancestral HP Table
        this.pedigreeTree[0] = sire.getName();
        this.pedigreeHP[0] = sire.getHereditaryPoints();
        this.pedigreeTree[1] = dam.getName();
        this.pedigreeHP[1] = dam.getHereditaryPoints();

        // Gen 2 (Indices 2..5): Paternal GP (2,3) from Sire (0,1), Maternal GP (4,5) from Dam (0,1)
        this.pedigreeTree[2] = sire.pedigreeTree[0];
        this.pedigreeHP[2]   = sire.pedigreeHP[0];
        this.pedigreeTree[3] = sire.pedigreeTree[1];
        this.pedigreeHP[3]   = sire.pedigreeHP[1];
        this.pedigreeTree[4] = dam.pedigreeTree[0];
        this.pedigreeHP[4]   = dam.pedigreeHP[0];
        this.pedigreeTree[5] = dam.pedigreeTree[1];
        this.pedigreeHP[5]   = dam.pedigreeHP[1];

        // Gen 3 (Indices 6..13): Sire's Gen 2 (2..5) -> 6..9; Dam's Gen 2 (2..5) -> 10..13
        for (int i = 0; i < 4; i++) {
            this.pedigreeTree[6 + i]  = sire.pedigreeTree[2 + i];
            this.pedigreeHP[6 + i]    = sire.pedigreeHP[2 + i];
            this.pedigreeTree[10 + i] = dam.pedigreeTree[2 + i];
            this.pedigreeHP[10 + i]   = dam.pedigreeHP[2 + i];
        }

        // Gen 4 (Indices 14..29): Sire's Gen 3 (6..13) -> 14..21; Dam's Gen 3 (6..13) -> 22..29
        for (int i = 0; i < 8; i++) {
            this.pedigreeTree[14 + i] = sire.pedigreeTree[6 + i];
            this.pedigreeHP[14 + i]   = sire.pedigreeHP[6 + i];
            this.pedigreeTree[22 + i] = dam.pedigreeTree[6 + i];
            this.pedigreeHP[22 + i]   = dam.pedigreeHP[6 + i];
        }

        // 2. Analyze 4-Generation Pedigree for Inbreeding vs. Linebreeding
        int closestPaternalGen = 99;
        int closestMaternalGen = 99;
        String sharedAncestorName = "";

        int[] patIndices = {0, 2, 3, 6, 7, 8, 9, 14, 15, 16, 17, 18, 19, 20, 21};
        int[] matIndices = {1, 4, 5, 10, 11, 12, 13, 22, 23, 24, 25, 26, 27, 28, 29};

        for (int pIdx : patIndices) {
            String pName = this.pedigreeTree[pIdx];
            if (pName == null || pName.isBlank()) continue;
            int pGen = getGenerationForPedigreeIndex(pIdx);
            for (int mIdx : matIndices) {
                String mName = this.pedigreeTree[mIdx];
                if (mName == null || mName.isBlank()) continue;
                if (pName.equalsIgnoreCase(mName)) {
                    int mGen = getGenerationForPedigreeIndex(mIdx);
                    if (pGen + mGen < closestPaternalGen + closestMaternalGen) {
                        closestPaternalGen = pGen;
                        closestMaternalGen = mGen;
                        sharedAncestorName = pName;
                    }
                }
            }
        }

        boolean hasInbreeding = !sharedAncestorName.isEmpty();
        boolean closeInbreeding = hasInbreeding && (closestPaternalGen <= 2 || closestMaternalGen <= 2);
        boolean miracleLinebreeding = hasInbreeding && !closeInbreeding; // e.g., 3x3, 3x4, 4x3, 4x4

        if (!hasInbreeding) {
            this.inbreedingSummary = "Outcross";
        } else if (closeInbreeding) {
            this.inbreedingSummary = String.format(Locale.ROOT, "§cClose Inbreeding: %s (%dx%d)",
                    sharedAncestorName, closestPaternalGen, closestMaternalGen);
        } else {
            this.inbreedingSummary = String.format(Locale.ROOT, "§eLinebred Cross: %s (%dx%d)",
                    sharedAncestorName, closestPaternalGen, closestMaternalGen);
        }

        // 3. Roll 100 Hereditary Points (HP) across the 100 Gene Slots:
        //    - 50 slots from Gen 1 Parents (25 Sire, 25 Dam)
        //    - 25 slots from Gen 2 Grandparents (indices 2..5)
        //    - 12 slots from Gen 3 Great-Grandparents (indices 6..13)
        //    - 6 slots from Gen 4 Great-Great-Grandparents (indices 14..29)
        //    - 7 slots from Wild / De Novo Variance (completing 100 slots!)
        int activeCount = 0;
        int slotCursor = 0;

        // 25 slots randomly drawn from Sire's 100 geneSlots
        for (int i = 0; i < 25; i++) {
            byte b = sire.geneSlots[random.nextInt(TOTAL_HP_SLOTS)];
            this.geneSlots[slotCursor++] = b;
            activeCount += b;
        }
        // 25 slots randomly drawn from Dam's 100 geneSlots
        for (int i = 0; i < 25; i++) {
            byte b = dam.geneSlots[random.nextInt(TOTAL_HP_SLOTS)];
            this.geneSlots[slotCursor++] = b;
            activeCount += b;
        }
        // 25 slots drawn from Gen 2 Grandparents (indices 2..5)
        for (int i = 0; i < 25; i++) {
            int gpIdx = 2 + (i % 4);
            int gpHp = this.pedigreeHP[gpIdx];
            byte b = (random.nextInt(100) < gpHp) ? (byte) 1 : (byte) 0;
            this.geneSlots[slotCursor++] = b;
            activeCount += b;
        }
        // 12 slots drawn from Gen 3 Great-Grandparents (indices 6..13)
        for (int i = 0; i < 12; i++) {
            int ggpIdx = 6 + (i % 8);
            int ggpHp = this.pedigreeHP[ggpIdx];
            byte b = (random.nextInt(100) < ggpHp) ? (byte) 1 : (byte) 0;
            this.geneSlots[slotCursor++] = b;
            activeCount += b;
        }
        // 6 slots drawn from Gen 4 Great-Great-Grandparents (indices 14..29)
        for (int i = 0; i < 6; i++) {
            int gggpIdx = 14 + random.nextInt(16);
            int gggpHp = this.pedigreeHP[gggpIdx];
            byte b = (random.nextInt(100) < gggpHp) ? (byte) 1 : (byte) 0;
            this.geneSlots[slotCursor++] = b;
            activeCount += b;
        }
        // Remaining 7 slots (6.25% Wild Variance)
        while (slotCursor < TOTAL_HP_SLOTS) {
            byte b = (random.nextInt(100) < 68) ? (byte) 1 : (byte) 0;
            this.geneSlots[slotCursor++] = b;
            activeCount += b;
        }

        // Check for Generation-Skipping (Atavism):
        // If a Grandparent or Great-Grandparent had much higher HP than the immediate Parents,
        // give a chance for those dormant champion genes to surge forward!
        int parentAvgHp = (sire.getHereditaryPoints() + dam.getHereditaryPoints()) / 2;
        int maxAncestorHp = parentAvgHp;
        for (int i = 2; i < PEDIGREE_SIZE; i++) {
            if (this.pedigreeTree[i] != null && !this.pedigreeTree[i].isBlank()) {
                maxAncestorHp = Math.max(maxAncestorHp, this.pedigreeHP[i]);
            }
        }
        this.atavismTriggered = false;
        if (maxAncestorHp >= parentAvgHp + 10 && random.nextDouble() < 0.35) {
            int atavismBoost = 6 + random.nextInt(Math.max(4, (maxAncestorHp - parentAvgHp)));
            activeCount = Math.min(100, activeCount + atavismBoost);
            this.atavismTriggered = true;
        } else if (activeCount >= parentAvgHp + 8) {
            this.atavismTriggered = true;
        }

        this.hereditaryPoints = Mth.clamp(activeCount, 35, 100);
        initGeneSlotsFromHP(this.hereditaryPoints, random);

        // 4. Calculate Distance & Track Aptitudes from Parents
        int avgMinDist = ((sire.getMinDistance() + dam.getMinDistance()) / 200) * 100;
        int avgMaxDist = ((sire.getMaxDistance() + dam.getMaxDistance()) / 200) * 100;
        int minShift = (random.nextInt(3) - 1) * 100;
        int maxShift = (random.nextInt(3) - 1) * 100;
        this.minDistance = Mth.clamp(avgMinDist + minShift, 1000, 3200);
        this.maxDistance = Mth.clamp(Math.max(this.minDistance + 200, avgMaxDist + maxShift), 1200, 4500);

        this.turfAptitude = Mth.clamp((int) Math.round((sire.getTurfAptitude() + dam.getTurfAptitude()) / 2.0) + (random.nextInt(3) - 1), 1, 10);
        this.dirtAptitude = Mth.clamp((int) Math.round((sire.getDirtAptitude() + dam.getDirtAptitude()) / 2.0) + (random.nextInt(3) - 1), 1, 10);

        // 5. Calculate Foal Max Stats & Initial Current Stats driven by 100 Hereditary Points (HP) + Parent Caps
        double hpFactor = 0.65 + (this.hereditaryPoints / 100.0) * 0.50; // 0.65 at 0 HP -> 1.00 at 70 HP -> 1.15 at 100 HP
        int inbreedingCapBonus = 0;
        this.inbredDepressionTriggered = false;

        if (miracleLinebreeding) {
            // 3x4 / 4x4 Linebreeding concentrates bloodline potential (+25 to +55 cap bonus)
            inbreedingCapBonus = 25 + random.nextInt(31);
            if (random.nextDouble() < 0.15) {
                this.inbredDepressionTriggered = true;
            }
        } else if (closeInbreeding) {
            // Close 1x2 / 2x2 / 2x3 Inbreeding: risky gamble! 65% risk of Inbreeding Depression, 35% high cap concentration
            if (random.nextDouble() < 0.65) {
                this.inbredDepressionTriggered = true;
                inbreedingCapBonus = -35;
            } else {
                inbreedingCapBonus = 40 + random.nextInt(41);
            }
        }

        int baseSpdMax  = (int) Math.round(((sire.getSpeedMax() + dam.getSpeedMax()) * 0.5) * hpFactor) + (random.nextInt(81) - 40) + inbreedingCapBonus;
        int baseStmMax  = (int) Math.round(((sire.getStaminaMax() + dam.getStaminaMax()) * 0.5) * hpFactor) + (random.nextInt(81) - 40) + inbreedingCapBonus;
        int rawSirePwr  = sire.hasGeneticTrait(TRAIT_LARGE_HOOVES) ? (sire.getPowerMax() / 2) : (sire.hasGeneticTrait(TRAIT_SMALL_HOOVES) ? Math.min(1000, sire.getPowerMax() * 2) : sire.getPowerMax());
        int rawDamPwr   = dam.hasGeneticTrait(TRAIT_LARGE_HOOVES) ? (dam.getPowerMax() / 2) : (dam.hasGeneticTrait(TRAIT_SMALL_HOOVES) ? Math.min(1000, dam.getPowerMax() * 2) : dam.getPowerMax());
        int basePwrMax  = (int) Math.round(((rawSirePwr + rawDamPwr) * 0.5) * hpFactor) + (random.nextInt(81) - 40) + inbreedingCapBonus;
        int baseGutsMax = (int) Math.round(((sire.getGutsMax() + dam.getGutsMax()) * 0.5) * hpFactor) + (random.nextInt(81) - 40) + inbreedingCapBonus;
        int baseWitsMax = (int) Math.round(((sire.getWitsMax() + dam.getWitsMax()) * 0.5) * hpFactor) + (random.nextInt(81) - 40) + inbreedingCapBonus;

        int maxAllowedCap = (miracleLinebreeding || closeInbreeding) ? 1050 : 1000;
        this.speedMax   = Mth.clamp(baseSpdMax, 450, maxAllowedCap);
        this.staminaMax = Mth.clamp(baseStmMax, 450, maxAllowedCap);
        this.powerMax   = Mth.clamp(basePwrMax, 450, maxAllowedCap);
        this.gutsMax    = Mth.clamp(baseGutsMax, 450, maxAllowedCap);
        this.witsMax    = Mth.clamp(baseWitsMax, 450, maxAllowedCap);

        this.speedCurrent   = clampStat((int) (this.speedMax * (0.30 + random.nextDouble() * 0.18)));
        this.staminaCurrent = clampStat((int) (this.staminaMax * (0.30 + random.nextDouble() * 0.18)));
        this.powerCurrent   = clampStat((int) (this.powerMax * (0.30 + random.nextDouble() * 0.18)));
        this.gutsCurrent    = clampStat((int) (this.gutsMax * (0.30 + random.nextDouble() * 0.18)));
        this.witsCurrent    = clampStat((int) (this.witsMax * (0.30 + random.nextDouble() * 0.18)));

        // 6. Inherit Running Strategy & Temperament
        int stratPick = random.nextInt(100);
        if (stratPick < 42) {
            this.strategy = sire.getStrategy();
        } else if (stratPick < 84) {
            this.strategy = dam.getStrategy();
        } else {
            this.strategy = STRATEGIES[random.nextInt(STRATEGIES.length)];
        }
        this.temperament = rollFoalTemperament(random, sire.getTemperament(), dam.getTemperament());
        this.personality = rollFoalPersonality(random, sire.getPersonality(), dam.getPersonality());
        this.competitiveLoserBoost = false;
        this.overfedStreak = 0;
        this.overweightExerciseMeters = 0.0;

        // 7. Inherit Genetic Traits (including Small Hooves, Large Hooves & the 8 New Traits!)
        this.geneticTraits.clear();
        boolean sireSmall = sire.hasGeneticTrait(TRAIT_SMALL_HOOVES);
        boolean damSmall  = dam.hasGeneticTrait(TRAIT_SMALL_HOOVES);
        boolean sireLarge = sire.hasGeneticTrait(TRAIT_LARGE_HOOVES);
        boolean damLarge  = dam.hasGeneticTrait(TRAIT_LARGE_HOOVES);

        int smallChance = (sireSmall && damSmall) ? 65 : ((sireSmall || damSmall) ? 38 : 6);
        int largeChance = (sireLarge && damLarge) ? 65 : ((sireLarge || damLarge) ? 38 : 6);
        int hoofRoll = random.nextInt(100);
        if (hoofRoll < smallChance) {
            // Initial power level is ALREADY calculated above; applyTraitAtBirth(TRAIT_SMALL_HOOVES)
            // permanently halves powerCurrent and powerMax and boosts speedMax!
            applyTraitAtBirth(TRAIT_SMALL_HOOVES);
        } else if (hoofRoll < smallChance + largeChance) {
            // applyTraitAtBirth(TRAIT_LARGE_HOOVES) doubles powerMax!
            applyTraitAtBirth(TRAIT_LARGE_HOOVES);
        }

        // Sex-Linked X-Chromosome Inheritance for X-Factor (Active vs. Hidden Carrier):
        // - Sires (XY) NEVER pass X-Factor to Sons (Colts get Y from Sire), and ALWAYS pass their X chromosome
        //   to Daughters (Fillies get 100% of Sire's X+ gene if Sire has X-Factor or is a hidden carrier).
        // - Dams (XX) pass one of their two X chromosomes equally to BOTH Sons and Daughters:
        //   - Double-copy Dam (X+X+, xFactorCopies >= 2): 100% chance to pass X+ to any foal.
        //   - Single-copy Dam (X+X, xFactorCopies == 1): 50% chance to pass X+ to any foal.
        boolean foalIsFemale = "Female".equalsIgnoreCase(this.gender);
        int inheritedXCopies = 0;

        // 1. Paternal X Chromosome (ONLY inherited by Daughters / Fillies!)
        if (foalIsFemale && sire.hasXFactorGene()) {
            inheritedXCopies += 1;
        }

        // 2. Maternal X Chromosome (Inherited by BOTH Sons / Colts and Daughters / Fillies!)
        int damCopies = dam.getXFactorCopies();
        if (damCopies >= 2) {
            inheritedXCopies += 1;
        } else if (damCopies == 1) {
            if (random.nextBoolean()) {
                inheritedXCopies += 1;
            }
        } else if (random.nextInt(200) == 0) {
            // Rare 0.5% de novo X-Factor mutation
            inheritedXCopies += 1;
        }

        this.xFactorCopies = Mth.clamp(inheritedXCopies, 0, foalIsFemale ? 2 : 1);

        // 3. Roll Phenotypic Expression vs. Hidden Recessive Carrier (Generation-Skipping):
        // - Double-Copy Filly (X+X+, copies == 2): 100% chance of active X-Factor expression!
        // - Single-Copy Filly (X+X, copies == 1): 20% chance of active X-Factor expression;
        //   80% chance of being a hidden carrier (no racing buffs herself, but passes X+ to 50% of her sons & daughters!).
        // - Colt with X+Y (copies == 1, from Dam): 50% chance of active X-Factor expression;
        //   50% chance of being a hidden carrier (passes X+ to 100% of his daughters at stud!).
        if (this.xFactorCopies >= 2) {
            this.geneticTraits.add(TRAIT_X_FACTOR);
        } else if (this.xFactorCopies == 1) {
            int expressChance = foalIsFemale ? 20 : 50;
            if (miracleLinebreeding) {
                expressChance += 15; // Miracle 3x4 / 4x4 linebreeding helps awaken dormant X-Factor!
            }
            if (random.nextInt(100) < expressChance) {
                this.geneticTraits.add(TRAIT_X_FACTOR);
            }
        }

        // Inherit other positive/negative autosomal traits from parents (40% chance per parent trait, +20% if linebred!)
        String[] otherTraits = {
                TRAIT_IRON_CONSTITUTION, TRAIT_EXPLOSIVE_GEAR, TRAIT_BORN_MUDDER,
                TRAIT_HILL_CLIMBER, TRAIT_LIGHTNING_STARTER, TRAIT_CORNERING_MAESTRO, TRAIT_DEEP_LUNG,
                TRAIT_SPRINTER_TWITCH, TRAIT_DIESEL_STAYER,
                TRAIT_GLASS_LEGS, TRAIT_ROARER_GENE, TRAIT_WEAK_CONSTITUTION, TRAIT_HIGH_STRUNG,
                TRAIT_SOFT_PALATE, TRAIT_BRITTLE_HOOVES
        };
        for (String tr : otherTraits) {
            if (tr.equals(TRAIT_DIESEL_STAYER) && hasGeneticTrait(TRAIT_SPRINTER_TWITCH)) continue;
            if (tr.equals(TRAIT_SPRINTER_TWITCH) && hasGeneticTrait(TRAIT_DIESEL_STAYER)) continue;
            boolean sHas = sire.hasGeneticTrait(tr);
            boolean dHas = dam.hasGeneticTrait(tr);
            int chance = 0;
            if (sHas && dHas) chance = 70;
            else if (sHas || dHas) chance = 40 + (miracleLinebreeding ? 20 : 0);
            else chance = 2;
            if (random.nextInt(100) < chance) {
                addGeneticTrait(tr);
            }
        }

        // If Inbreeding Depression triggered, inflict a negative congenital trait!
        if (this.inbredDepressionTriggered) {
            String[] negPool = {
                    TRAIT_GLASS_LEGS, TRAIT_ROARER_GENE, TRAIT_WEAK_CONSTITUTION,
                    TRAIT_HIGH_STRUNG, TRAIT_SOFT_PALATE, TRAIT_BRITTLE_HOOVES
            };
            addGeneticTrait(negPool[random.nextInt(negPool.length)]);
            if (closeInbreeding && random.nextBoolean()) {
                addGeneticTrait(negPool[random.nextInt(negPool.length)]);
            }
        }

        // 8. Roll True Pure-White Coat Inheritance (Shirayuki / Sodashi dominant W gene + rare de novo mutation)
        if (sire.isPureWhiteCoat() && dam.isPureWhiteCoat()) {
            this.pureWhiteCoat = (random.nextInt(100) < 75);
        } else if (sire.isPureWhiteCoat() || dam.isPureWhiteCoat()) {
            this.pureWhiteCoat = (random.nextInt(100) < 50);
        } else {
            this.pureWhiteCoat = (random.nextInt(500) == 0); // 0.2% de novo mutation
        }

        this.hunger = 100.0;
        this.thirst = 100.0;
        this.fatigue = 0.0;
        this.bond = 30.0; // Bred foals start with slightly higher initial bond!
        this.mood = 65.0;
        this.careerHistory.clear();
        this.totalEarnings = 0L;
    }

    public static int getGenerationForPedigreeIndex(int idx) {
        if (idx <= 1) return 1;
        if (idx <= 5) return 2;
        if (idx <= 13) return 3;
        return 4;
    }
}