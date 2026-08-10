-- 이 파일은 생성물입니다. 직접 편집하지 마세요.
-- 원본: ml-service/app/domain/seasons.py
-- 재생성: uv run python scripts/export_palettes.py --format sql -o <이 파일>
--
-- 팔레트의 소유권이 Spring(DB)에 있는 이유는 ADR-005 참조.
-- 요약하면 큐레이션은 측정이 아니므로, 색 하나 바꾸는 데 추론 서버를
-- 재배포해야 하는 구조를 피하려는 것이다.

-- 🌸 봄 웜
INSERT INTO season_profiles (code, undertone, label_ko, label_en, emoji, description) VALUES ('spring_warm', 'warm', '봄 웜', 'Spring Warm', '🌸', '노란기가 도는 밝고 맑은 피부톤입니다. 채도가 높고 투명한 색이 얼굴에 생기를 더하며, 탁하거나 무거운 색은 혈색을 눌러버립니다.');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('spring_warm', 0, '밝은');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('spring_warm', 1, '따뜻한');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('spring_warm', 2, '생기있는');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('spring_warm', 3, '선명한');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('spring_warm', 'BEST', 0, '코랄', '#FF7F50');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('spring_warm', 'BEST', 1, '피치', '#FFCBA4');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('spring_warm', 'BEST', 2, '아이보리', '#FFF8E7');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('spring_warm', 'BEST', 3, '라이트 옐로우그린', '#C5E384');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('spring_warm', 'BEST', 4, '살몬 핑크', '#FF91A4');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('spring_warm', 'BEST', 5, '카멜', '#C19A6B');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('spring_warm', 'BEST', 6, '터콰이즈', '#40E0D0');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('spring_warm', 'BEST', 7, '골든 옐로우', '#FFD34E');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('spring_warm', 'WORST', 0, '차콜 그레이', '#36454F');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('spring_warm', 'WORST', 1, '퓨어 블랙', '#000000');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('spring_warm', 'WORST', 2, '딥 버건디', '#5C1F33');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('spring_warm', 'WORST', 3, '더스티 모브', '#8B7B8B');
INSERT INTO styling_tips (season_code, display_order, tip) VALUES ('spring_warm', 0, '골드 계열 액세서리가 피부 광택과 어울립니다.');
INSERT INTO styling_tips (season_code, display_order, tip) VALUES ('spring_warm', 1, '블랙 대신 다크 브라운이나 네이비로 무게를 잡으세요.');
INSERT INTO styling_tips (season_code, display_order, tip) VALUES ('spring_warm', 2, '메이크업은 코랄·피치 계열 블러셔가 안전합니다.');

-- ☀️ 여름 쿨
INSERT INTO season_profiles (code, undertone, label_ko, label_en, emoji, description) VALUES ('summer_cool', 'cool', '여름 쿨', 'Summer Cool', '☀️', '푸른기가 도는 밝고 부드러운 피부톤입니다. 채도를 한 톤 낮춘 뮤트한 색이 가장 잘 맞고, 쨍한 원색은 얼굴보다 옷이 먼저 보입니다.');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('summer_cool', 0, '밝은');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('summer_cool', 1, '차가운');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('summer_cool', 2, '부드러운');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('summer_cool', 3, '은은한');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('summer_cool', 'BEST', 0, '라벤더', '#B57EDC');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('summer_cool', 'BEST', 1, '파우더 블루', '#B0C4DE');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('summer_cool', 'BEST', 2, '로즈 핑크', '#E8A5B8');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('summer_cool', 'BEST', 3, '소프트 그레이', '#B8B8C0');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('summer_cool', 'BEST', 4, '민트', '#A8D8C8');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('summer_cool', 'BEST', 5, '스카이 블루', '#87CEEB');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('summer_cool', 'BEST', 6, '더스티 로즈', '#C4A0A8');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('summer_cool', 'BEST', 7, '페일 퍼플', '#D8BFD8');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('summer_cool', 'WORST', 0, '오렌지', '#FF7518');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('summer_cool', 'WORST', 1, '머스타드', '#E1AD01');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('summer_cool', 'WORST', 2, '카멜', '#C19A6B');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('summer_cool', 'WORST', 3, '골드', '#D4AF37');
INSERT INTO styling_tips (season_code, display_order, tip) VALUES ('summer_cool', 0, '실버·화이트골드 액세서리를 권합니다.');
INSERT INTO styling_tips (season_code, display_order, tip) VALUES ('summer_cool', 1, '톤온톤 배색이 가장 자연스럽습니다.');
INSERT INTO styling_tips (season_code, display_order, tip) VALUES ('summer_cool', 2, '메이크업은 로즈·핑크 계열로 통일하세요.');

-- 🍂 가을 웜
INSERT INTO season_profiles (code, undertone, label_ko, label_en, emoji, description) VALUES ('autumn_warm', 'warm', '가을 웜', 'Autumn Warm', '🍂', '노란기가 도는 깊고 차분한 피부톤입니다. 채도를 낮춘 어스 톤이 피부의 깊이를 살리며, 밝은 파스텔은 얼굴을 창백하게 만듭니다.');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('autumn_warm', 0, '깊은');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('autumn_warm', 1, '따뜻한');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('autumn_warm', 2, '차분한');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('autumn_warm', 3, '고급스러운');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('autumn_warm', 'BEST', 0, '머스타드', '#D4A017');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('autumn_warm', 'BEST', 1, '카키', '#78866B');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('autumn_warm', 'BEST', 2, '테라코타', '#C96A50');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('autumn_warm', 'BEST', 3, '브릭 레드', '#9C3527');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('autumn_warm', 'BEST', 4, '올리브', '#6B7B3A');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('autumn_warm', 'BEST', 5, '초콜릿 브라운', '#5D4037');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('autumn_warm', 'BEST', 6, '딥 오렌지', '#CC5500');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('autumn_warm', 'BEST', 7, '캐멀 베이지', '#A9825C');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('autumn_warm', 'WORST', 0, '아이시 핑크', '#FFD1DC');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('autumn_warm', 'WORST', 1, '퓨어 화이트', '#FFFFFF');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('autumn_warm', 'WORST', 2, '실버', '#C0C0C0');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('autumn_warm', 'WORST', 3, '페일 라벤더', '#E6E6FA');
INSERT INTO styling_tips (season_code, display_order, tip) VALUES ('autumn_warm', 0, '앤티크 골드·브론즈 액세서리가 잘 어울립니다.');
INSERT INTO styling_tips (season_code, display_order, tip) VALUES ('autumn_warm', 1, '화이트가 필요하면 오프화이트나 크림으로 대체하세요.');
INSERT INTO styling_tips (season_code, display_order, tip) VALUES ('autumn_warm', 2, '메이크업은 벽돌빛 브릭·브라운 계열을 권합니다.');

-- ❄️ 겨울 쿨
INSERT INTO season_profiles (code, undertone, label_ko, label_en, emoji, description) VALUES ('winter_cool', 'cool', '겨울 쿨', 'Winter Cool', '❄️', '푸른기가 도는 깊고 선명한 피부톤입니다. 명암 대비가 강한 배색이 이목구비를 또렷하게 만들고, 탁한 중간톤은 얼굴을 흐리게 합니다.');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('winter_cool', 0, '선명한');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('winter_cool', 1, '차가운');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('winter_cool', 2, '대비가 강한');
INSERT INTO season_keywords (season_code, display_order, keyword) VALUES ('winter_cool', 3, '도회적인');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('winter_cool', 'BEST', 0, '퓨어 화이트', '#FFFFFF');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('winter_cool', 'BEST', 1, '트루 블랙', '#000000');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('winter_cool', 'BEST', 2, '로열 블루', '#1F4FD8');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('winter_cool', 'BEST', 3, '마젠타', '#D6006E');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('winter_cool', 'BEST', 4, '에메랄드', '#00926C');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('winter_cool', 'BEST', 5, '버건디', '#7B1E3A');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('winter_cool', 'BEST', 6, '실버', '#C0C0C0');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('winter_cool', 'BEST', 7, '아이시 핑크', '#FFD1DC');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('winter_cool', 'WORST', 0, '베이지', '#E8DCC4');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('winter_cool', 'WORST', 1, '카멜', '#C19A6B');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('winter_cool', 'WORST', 2, '머스타드', '#E1AD01');
INSERT INTO palette_colors (season_code, palette_kind, display_order, name, hex) VALUES ('winter_cool', 'WORST', 3, '올리브 브라운', '#6B5B3A');
INSERT INTO styling_tips (season_code, display_order, tip) VALUES ('winter_cool', 0, '실버·플래티넘 액세서리가 가장 잘 맞습니다.');
INSERT INTO styling_tips (season_code, display_order, tip) VALUES ('winter_cool', 1, '블랙&화이트 대비 배색을 적극 활용하세요.');
INSERT INTO styling_tips (season_code, display_order, tip) VALUES ('winter_cool', 2, '메이크업은 레드·플럼 계열로 또렷하게 마무리합니다.');

