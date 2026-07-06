CREATE TABLE IF NOT EXISTS customers (
    cust_id        CHAR(10)     NOT NULL PRIMARY KEY,
    cust_name      VARCHAR(60)  NOT NULL,
    cust_name_kana VARCHAR(80)  NOT NULL,
    cust_status    CHAR(1)      NOT NULL,
    tier           CHAR(1)      NOT NULL DEFAULT 'B',
    phone          VARCHAR(20),
    address        VARCHAR(120),
    created_at     TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP(0) NOT NULL DEFAULT NOW()
);

-- ── CSRCH-AND テストデータ (カナ 前方一致 AND 電話 前方一致) ──────────────
INSERT INTO customers VALUES ('0000000001', '田中 太郎' , 'タナカ タロウ' , 'A', 'B', '0312345678', '東京都千代田区1-1', NOW(), NOW());
INSERT INTO customers VALUES ('0000000002', '田中 花子' , 'タナカ ハナコ' , 'A', 'B', '0312345679', '東京都渋谷区2-2' , NOW(), NOW());
INSERT INTO customers VALUES ('0000000003', '鈴木 一郎' , 'スズキ イチロー', 'A', 'B', '0611112222', '東京都新宿区3-3' , NOW(), NOW());

-- ── CSRCH-BY-ADDRESS テストデータ ───────────────────────────────────────
INSERT INTO customers VALUES ('0000000010', '山田 恵子' , 'ヤマダ ケイコ' , 'A', 'G', '04566667777', '神奈川県横浜市渋谷区', NOW(), NOW());
INSERT INTO customers VALUES ('0000000011', '山田 稔'   , 'ヤマダ ミノル' , 'A', 'S', '04566668888', '神奈川県横浜市緑区' , NOW(), NOW());

-- ── CSRCH-LIST-PAGED テストデータ (10 件) ─────────────────────────────
INSERT INTO customers VALUES ('0000000100', '金田 Ａ'   , 'キネダ エー'  , 'A', 'B', '0900000001', '東京都北区1'     , NOW(), NOW());
INSERT INTO customers VALUES ('0000000101', '金田 Ｂ'   , 'キネダ ビー'  , 'A', 'B', '0900000002', '東京都北区2'     , NOW(), NOW());
INSERT INTO customers VALUES ('0000000102', '金田 Ｃ'   , 'キネダ シー'  , 'A', 'B', '0900000003', '東京都北区3'     , NOW(), NOW());
INSERT INTO customers VALUES ('0000000103', '金田 Ｄ'   , 'キネダ ディー', 'A', 'B', '0900000004', '東京都北区4'     , NOW(), NOW());
INSERT INTO customers VALUES ('0000000104', '金田 Ｅ'   , 'キネダ イー'  , 'A', 'B', '0900000005', '東京都北区5'     , NOW(), NOW());
INSERT INTO customers VALUES ('0000000105', '金田 Ｆ'   , 'キネダ エフ'  , 'A', 'B', '0900000006', '東京都北区6'     , NOW(), NOW());
INSERT INTO customers VALUES ('0000000106', '金田 Ｇ'   , 'キネダ ジー'  , 'A', 'B', '0900000007', '東京都北区7'     , NOW(), NOW());
INSERT INTO customers VALUES ('0000000107', '金田 Ｈ'   , 'キネダ エイチ', 'A', 'B', '0900000008', '東京都北区8'     , NOW(), NOW());
INSERT INTO customers VALUES ('0000000108', '金田 Ｉ'   , 'キネダ アイ'  , 'A', 'B', '0900000009', '東京都北区9'     , NOW(), NOW());
INSERT INTO customers VALUES ('0000000109', '金田 Ｊ'   , 'キネダ ジェー', 'A', 'B', '0900000010', '東京都北区10'    , NOW(), NOW());
