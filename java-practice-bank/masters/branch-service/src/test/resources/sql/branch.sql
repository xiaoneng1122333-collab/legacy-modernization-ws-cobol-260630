DROP TABLE IF EXISTS branches;
CREATE TABLE branches (
    branch_code      CHAR(3)      NOT NULL PRIMARY KEY,
    branch_name      VARCHAR(60)  NOT NULL,
    branch_name_kana VARCHAR(80)  NOT NULL,
    branch_type      CHAR(1)      NOT NULL,
    address          VARCHAR(120),
    phone            VARCHAR(20),
    created_at       TIMESTAMP(0) NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP(0) NOT NULL DEFAULT NOW()
);

-- 10 rows of seed data matching COBOL branches-mvp.dat (Tokyo x4, Osaka x3, Nagoya x2, Fukuoka x1)
INSERT INTO branches (branch_code, branch_name, branch_name_kana, branch_type, address, phone) VALUES ('001', '東京本店',       'トウキョウホンテン', 'H', '東京都千代田区丸の内1-1-1', '03-1111-0001');
INSERT INTO branches (branch_code, branch_name, branch_name_kana, branch_type, address, phone) VALUES ('002', '新宿支店',       'シンジュク',        'B', '東京都新宿区西新宿2-2-2',   '03-2222-0002');
INSERT INTO branches (branch_code, branch_name, branch_name_kana, branch_type, address, phone) VALUES ('003', '渋谷支店',       'シブヤ',            'B', '東京都渋谷区神宮前3-3-3',   '03-3333-0003');
INSERT INTO branches (branch_code, branch_name, branch_name_kana, branch_type, address, phone) VALUES ('004', '品川支店',       'シナガワ',          'B', '東京都港区高輪4-4-4',       '03-4444-0004');
INSERT INTO branches (branch_code, branch_name, branch_name_kana, branch_type, address, phone) VALUES ('005', '大阪本店',       'オオサカホンテン',   'H', '大阪府大阪市北区梅田5-5-5', '06-5555-0005');
INSERT INTO branches (branch_code, branch_name, branch_name_kana, branch_type, address, phone) VALUES ('006', '心斎橋支店',     'シンサイバシ',       'B', '大阪府大阪市中央区6-6-6',   '06-6666-0006');
INSERT INTO branches (branch_code, branch_name, branch_name_kana, branch_type, address, phone) VALUES ('007', '堺支店',         'サカイ',            'B', '大阪府堺市堺区7-7-7',       '06-7777-0007');
INSERT INTO branches (branch_code, branch_name, branch_name_kana, branch_type, address, phone) VALUES ('008', '名古屋本店',     'ナゴヤホンテン',     'H', '愛知県名古屋市中区8-8-8',   '052-8888-0008');
INSERT INTO branches (branch_code, branch_name, branch_name_kana, branch_type, address, phone) VALUES ('009', '栄支店',         'サカエ',            'B', '愛知県名古屋市東区9-9-9',   '052-9999-0009');
INSERT INTO branches (branch_code, branch_name, branch_name_kana, branch_type, address, phone) VALUES ('010', '福岡本店',       'フクオカホンテン',   'H', '福岡県福岡市中央区10-10',  '092-1010-0010');
