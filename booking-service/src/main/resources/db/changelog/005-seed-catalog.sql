--liquibase formatted sql

--changeset apextick:005-01-series context:demo
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT count(*) FROM series
INSERT INTO series (slug, name, short_name, sport, tint, kicker, blurb, story, image, mobile_hero_image, currency, currency_symbol, cities, scale) VALUES
('icc-t20-2026', 'ICC T20 World Cup 2026', 'T20 World Cup', 'CRICKET', '#46b1ff', 'International / Cricket', 'Twenty nations, one trophy, decided across the subcontinent under lights.', 'The global game arrives at full volume. Forty eight matches, twenty teams, and a knockout run where a single over can rewrite a nation''s summer. From the first ball in Colombo to the final under the Ahmedabad floodlights, this is cricket at its loudest.', '1540747913346-19e32dc3e97e', '/virat.jpg', 'INR', '₹', '["Ahmedabad","Mumbai","Kolkata","Colombo"]', '48 matches'),
('ipl-2026', 'Indian Premier League', 'IPL 2026', 'CRICKET', '#ff3d77', 'Franchise / Cricket', 'Ten cities, floodlit nights, and the most relentless league in the sport.', 'Two months of full houses, music between the overs, and rivalries that shut down whole cities. The IPL is cricket rebuilt for the night: faster, louder, and impossible to look away from. Every seat is inside the noise.', '/2020-t20w-worldcup-final-mcg.jpg', '/aus.webp', 'INR', '₹', '["Mumbai","Chennai","Bengaluru","Kolkata","Ahmedabad"]', '74 nights'),
('premier-league', 'Premier League', 'Premier League', 'FOOTBALL', '#ed3f2c', 'Matchday / Football', 'The English season at full tilt, from the first whistle to stoppage time.', 'Floodlights, terraces, and ninety minutes that decide a season. The Premier League is matchday culture distilled: the walk to the ground, the roar at kickoff, and the away end that never sits down. Iconic stadiums, historic rivalries, every weekend.', '/pre.jpg', '/ronaldo.jpg', 'GBP', '£', '["London","Manchester","Liverpool","Newcastle","Birmingham"]', '38 rounds'),
('fifa-world-cup', 'FIFA World Cup', 'World Cup', 'FOOTBALL', '#00a859', 'International / Football', 'The world''s game, played on the biggest stage. Thirty-two nations, one dream.', 'A month of pure footballing drama. The FIFA World Cup brings the globe together for a festival of sport, where legends are made and history is written. Every four years, the world stops to watch.', '1647849402208-01775e9c9fb3', '/fifa.jpg', 'USD', '$', '["New York","Los Angeles","Miami","Dallas","Toronto"]', '64 matches');
--rollback DELETE FROM series;

--changeset apextick:005-02-teams context:demo
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT count(*) FROM teams
INSERT INTO teams (sport, name, short_code, monogram, color, flag, logo) VALUES
('CRICKET', 'India', 'IND', 'IN', '#2a4fb0', 'in', NULL),
('CRICKET', 'Australia', 'AUS', 'AU', '#0e7a4e', 'au', NULL),
('CRICKET', 'England', 'ENG', 'EN', '#1c2f5a', 'gb-eng', NULL),
('CRICKET', 'Pakistan', 'PAK', 'PK', '#0b6e4f', 'pk', NULL),
('CRICKET', 'South Africa', 'RSA', 'SA', '#0a6e3b', 'za', NULL),
('CRICKET', 'New Zealand', 'NZL', 'NZ', '#3a3f47', 'nz', NULL),
('CRICKET', 'Sri Lanka', 'SL', 'SL', '#1b4f9c', 'lk', NULL),
('CRICKET', 'West Indies', 'WI', 'WI', '#6e0e2b', NULL, NULL),
('CRICKET', 'Mumbai Indians', 'MI', 'MI', '#0a4da2', NULL, NULL),
('CRICKET', 'Chennai Super Kings', 'CSK', 'CS', '#d9a400', NULL, NULL),
('CRICKET', 'Royal Challengers Bengaluru', 'RCB', 'RC', '#c8102e', NULL, NULL),
('CRICKET', 'Kolkata Knight Riders', 'KKR', 'KK', '#5b3a92', NULL, NULL),
('CRICKET', 'Gujarat Titans', 'GT', 'GT', '#143b66', NULL, NULL),
('CRICKET', 'Rajasthan Royals', 'RR', 'RR', '#e6007e', NULL, NULL),
('CRICKET', 'Delhi Capitals', 'DC', 'DC', '#17449b', NULL, NULL),
('CRICKET', 'Sunrisers Hyderabad', 'SRH', 'SH', '#f26522', NULL, NULL),
('FOOTBALL', 'Arsenal', 'ARS', 'AR', '#ef0107', NULL, '/logos/ars.png'),
('FOOTBALL', 'Manchester City', 'MCI', 'MC', '#6cabdd', NULL, '/logos/mci.png'),
('FOOTBALL', 'Liverpool', 'LIV', 'LI', '#c8102e', NULL, '/logos/liv.png'),
('FOOTBALL', 'Manchester United', 'MUN', 'MU', '#da291c', NULL, '/logos/mun.png'),
('FOOTBALL', 'Chelsea', 'CHE', 'CH', '#1f6dd0', NULL, '/logos/che.png'),
('FOOTBALL', 'Tottenham Hotspur', 'TOT', 'TH', '#9fb4d8', NULL, '/logos/tot.png'),
('FOOTBALL', 'Newcastle United', 'NEW', 'NU', '#cfd2d4', NULL, '/logos/new.png'),
('FOOTBALL', 'Aston Villa', 'AVL', 'AV', '#8c2754', NULL, '/logos/avl.png'),
('FOOTBALL', 'United States', 'USA', 'US', '#1a3a6b', 'us', NULL),
('FOOTBALL', 'Paraguay', 'PAR', 'PY', '#d52b1e', 'py', NULL),
('FOOTBALL', 'Brazil', 'BRA', 'BR', '#009c3b', 'br', NULL),
('FOOTBALL', 'Morocco', 'MAR', 'MA', '#c1272d', 'ma', NULL),
('FOOTBALL', 'England', 'ENG', 'EN', '#1c2f5a', 'gb-eng', NULL),
('FOOTBALL', 'Croatia', 'CRO', 'HR', '#d10000', 'hr', NULL),
('FOOTBALL', 'Argentina', 'ARG', 'AG', '#75aadb', 'ar', NULL),
('FOOTBALL', 'France', 'FRA', 'FR', '#0055a4', 'fr', NULL);
--rollback DELETE FROM teams;

--changeset apextick:005-03-events context:demo
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT count(*) FROM events WHERE slug = 'india-pakistan-group-stage'
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('India v Pakistan', 'Narendra Modi Stadium', TIMESTAMP '2026-02-21 19:00:00' AT TIME ZONE 'Asia/Kolkata', 'india-pakistan-group-stage', (SELECT id FROM series WHERE slug = 'icc-t20-2026'), 'CRICKET', (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'IND'), (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'PAK'), 'Asia/Kolkata', 'Ahmedabad', 'India', 'Group Stage', 'SELLING_FAST', '1540747913346-19e32dc3e97e', 'The fixture that stops a billion clocks. A hundred and thirty thousand seats, one rivalry, settled in three hours.', 'INR');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Australia v England', 'Eden Gardens', TIMESTAMP '2026-02-26 19:00:00' AT TIME ZONE 'Asia/Kolkata', 'australia-england-super-8', (SELECT id FROM series WHERE slug = 'icc-t20-2026'), 'CRICKET', (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'AUS'), (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'ENG'), 'Asia/Kolkata', 'Kolkata', 'India', 'Super 8', 'ONSALE', '1593766827228-8737b4534aa6', 'Two heavyweight white-ball sides under the Eden Gardens roar, with a semi-final place on the line.', 'INR');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('South Africa v New Zealand', 'R. Premadasa Stadium', TIMESTAMP '2026-02-23 15:30:00' AT TIME ZONE 'Asia/Colombo', 'south-africa-new-zealand-super-8', (SELECT id FROM series WHERE slug = 'icc-t20-2026'), 'CRICKET', (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'RSA'), (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'NZL'), 'Asia/Colombo', 'Colombo', 'Sri Lanka', 'Super 8', 'ONSALE', '1589801258579-18e091f4ca26', 'A Colombo afternoon, spin-friendly and tense, between two sides who know each other far too well.', 'INR');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('India v Australia', 'Wankhede Stadium', TIMESTAMP '2026-03-04 19:00:00' AT TIME ZONE 'Asia/Kolkata', 'india-australia-semi-final', (SELECT id FROM series WHERE slug = 'icc-t20-2026'), 'CRICKET', (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'IND'), (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'AUS'), 'Asia/Kolkata', 'Mumbai', 'India', 'Semi Final', 'SELLING_FAST', '1540747913346-19e32dc3e97e', 'Mumbai under lights, a sea-breeze chasing the ball to the boundary, and a final spot for the winner.', 'INR');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('India v England', 'Narendra Modi Stadium', TIMESTAMP '2026-03-08 19:00:00' AT TIME ZONE 'Asia/Kolkata', 'world-cup-final', (SELECT id FROM series WHERE slug = 'icc-t20-2026'), 'CRICKET', (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'IND'), (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'ENG'), 'Asia/Kolkata', 'Ahmedabad', 'India', 'Final', 'FINAL_RELEASE', '1593766827228-8737b4534aa6', 'One night, one trophy, the largest cricket ground on earth at capacity. The last seats of the tournament.', 'INR');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Mumbai Indians v Chennai Super Kings', 'Wankhede Stadium', TIMESTAMP '2026-04-12 19:30:00' AT TIME ZONE 'Asia/Kolkata', 'mumbai-indians-chennai-super-kings', (SELECT id FROM series WHERE slug = 'ipl-2026'), 'CRICKET', (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'MI'), (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'CSK'), 'Asia/Kolkata', 'Mumbai', 'India', 'League / Night 9', 'SELLING_FAST', '1624526267942-ab0ff8a3e972', 'Blue against yellow, the league''s oldest grudge, on a Wankhede deck built for sixes.', 'INR');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Royal Challengers Bengaluru v Kolkata Knight Riders', 'M. Chinnaswamy Stadium', TIMESTAMP '2026-04-15 19:30:00' AT TIME ZONE 'Asia/Kolkata', 'bengaluru-kolkata-night', (SELECT id FROM series WHERE slug = 'ipl-2026'), 'CRICKET', (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'RCB'), (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'KKR'), 'Asia/Kolkata', 'Bengaluru', 'India', 'League / Night 12', 'ONSALE', '1531415074968-036ba1b575da', 'Chinnaswamy is the shortest boundary on the circuit. Bring an appetite for run chases.', 'INR');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Gujarat Titans v Rajasthan Royals', 'Narendra Modi Stadium', TIMESTAMP '2026-04-18 15:30:00' AT TIME ZONE 'Asia/Kolkata', 'gujarat-titans-rajasthan-royals', (SELECT id FROM series WHERE slug = 'ipl-2026'), 'CRICKET', (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'GT'), (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'RR'), 'Asia/Kolkata', 'Ahmedabad', 'India', 'League / Night 15', 'ONSALE', '1589801258579-18e091f4ca26', 'A day game in Ahmedabad, two of the league''s youngest sides swinging from ball one.', 'INR');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Chennai Super Kings v Mumbai Indians', 'M. A. Chidambaram Stadium', TIMESTAMP '2026-04-25 19:30:00' AT TIME ZONE 'Asia/Kolkata', 'chennai-mumbai-return', (SELECT id FROM series WHERE slug = 'ipl-2026'), 'CRICKET', (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'CSK'), (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'MI'), 'Asia/Kolkata', 'Chennai', 'India', 'League / Night 22', 'SELLING_FAST', '1593766827228-8737b4534aa6', 'The return leg at Chepauk, where the crowd noise has its own weather system.', 'INR');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Mumbai Indians v Gujarat Titans', 'Eden Gardens', TIMESTAMP '2026-05-24 19:30:00' AT TIME ZONE 'Asia/Kolkata', 'qualifier-one', (SELECT id FROM series WHERE slug = 'ipl-2026'), 'CRICKET', (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'MI'), (SELECT id FROM teams WHERE sport = 'CRICKET' AND short_code = 'GT'), 'Asia/Kolkata', 'Kolkata', 'India', 'Qualifier 1', 'FINAL_RELEASE', '1624526267942-ab0ff8a3e972', 'Win and you are in the final. Lose and there is one more road back. The playoffs begin in Kolkata.', 'INR');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Arsenal v Manchester City', 'Emirates Stadium', TIMESTAMP '2026-02-22 16:30:00' AT TIME ZONE 'Europe/London', 'arsenal-manchester-city', (SELECT id FROM series WHERE slug = 'premier-league'), 'FOOTBALL', (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'ARS'), (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'MCI'), 'Europe/London', 'London', 'England', 'Matchday 27', 'SELLING_FAST', '1522778119026-d647f0596c20', 'The title race in ninety minutes. North London hosts the champions with everything still to play for.', 'GBP');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Liverpool v Manchester United', 'Anfield', TIMESTAMP '2026-03-01 16:00:00' AT TIME ZONE 'Europe/London', 'liverpool-manchester-united', (SELECT id FROM series WHERE slug = 'premier-league'), 'FOOTBALL', (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'LIV'), (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'MUN'), 'Europe/London', 'Liverpool', 'England', 'Matchday 28', 'SELLING_FAST', '1431324155629-1a6deb1dec8d', 'England''s biggest rivalry, under the Anfield lights, with the Kop in full voice from the first minute.', 'GBP');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Manchester City v Tottenham Hotspur', 'Etihad Stadium', TIMESTAMP '2026-03-07 15:00:00' AT TIME ZONE 'Europe/London', 'manchester-city-tottenham', (SELECT id FROM series WHERE slug = 'premier-league'), 'FOOTBALL', (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'MCI'), (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'TOT'), 'Europe/London', 'Manchester', 'England', 'Matchday 29', 'ONSALE', '1429962714451-bb934ecdc4ec', 'A Saturday three o''clock at the Etihad, the way the league was meant to be watched.', 'GBP');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Newcastle United v Chelsea', 'St James'' Park', TIMESTAMP '2026-03-14 12:30:00' AT TIME ZONE 'Europe/London', 'newcastle-chelsea', (SELECT id FROM series WHERE slug = 'premier-league'), 'FOOTBALL', (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'NEW'), (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'CHE'), 'Europe/London', 'Newcastle', 'England', 'Matchday 30', 'ONSALE', '1574629810360-7efbbe195018', 'An early kickoff on Tyneside, where the noise comes down the stands like weather off the river.', 'GBP');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Aston Villa v Arsenal', 'Villa Park', TIMESTAMP '2026-03-21 17:30:00' AT TIME ZONE 'Europe/London', 'aston-villa-arsenal', (SELECT id FROM series WHERE slug = 'premier-league'), 'FOOTBALL', (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'AVL'), (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'ARS'), 'Europe/London', 'Birmingham', 'England', 'Matchday 31', 'FINAL_RELEASE', '1551958219-acbc608c6377', 'A floodlit evening at Villa Park, with the visitors chasing every point in the run-in.', 'GBP');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Tottenham Hotspur v Chelsea', 'Tottenham Hotspur Stadium', TIMESTAMP '2026-04-04 17:30:00' AT TIME ZONE 'Europe/London', 'tottenham-chelsea-derby', (SELECT id FROM series WHERE slug = 'premier-league'), 'FOOTBALL', (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'TOT'), (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'CHE'), 'Europe/London', 'London', 'England', 'Matchday 32', 'SELLING_FAST', '1429962714451-bb934ecdc4ec', 'A London derby with European places on the line, under the sharpest roof in the league.', 'GBP');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('United States v Paraguay', 'SoFi Stadium', TIMESTAMP '2026-06-12 19:00:00' AT TIME ZONE 'America/Los_Angeles', 'usa-paraguay-group-stage', (SELECT id FROM series WHERE slug = 'fifa-world-cup'), 'FOOTBALL', (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'USA'), (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'PAR'), 'America/Los_Angeles', 'Los Angeles', 'United States', 'Group Stage', 'ONSALE', '1647849402208-01775e9c9fb3', 'The opening night of a home World Cup, Los Angeles under the lights and a nation willing its team forward.', 'USD');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Brazil v Morocco', 'MetLife Stadium', TIMESTAMP '2026-06-13 16:00:00' AT TIME ZONE 'America/New_York', 'brazil-morocco-group-stage', (SELECT id FROM series WHERE slug = 'fifa-world-cup'), 'FOOTBALL', (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'BRA'), (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'MAR'), 'America/New_York', 'New York', 'United States', 'Group Stage', 'SELLING_FAST', '1522778119026-d647f0596c20', 'The samba against the surprise of the last tournament, a group-stage clash with the weight of a knockout.', 'USD');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('England v Croatia', 'AT&T Stadium', TIMESTAMP '2026-06-17 18:00:00' AT TIME ZONE 'America/Chicago', 'england-croatia-group-stage', (SELECT id FROM series WHERE slug = 'fifa-world-cup'), 'FOOTBALL', (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'ENG'), (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'CRO'), 'America/Chicago', 'Dallas', 'United States', 'Group Stage', 'ONSALE', '1429962714451-bb934ecdc4ec', 'A rematch of a semi-final that still stings, played out under the closed roof in Dallas.', 'USD');
INSERT INTO events (name, venue, starts_at, slug, series_id, sport, home_team_id, away_team_id, time_zone, city, country, stage, status, image, blurb, currency) VALUES ('Argentina v France', 'MetLife Stadium', TIMESTAMP '2026-07-19 15:00:00' AT TIME ZONE 'America/New_York', 'world-cup-final-2026', (SELECT id FROM series WHERE slug = 'fifa-world-cup'), 'FOOTBALL', (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'ARG'), (SELECT id FROM teams WHERE sport = 'FOOTBALL' AND short_code = 'FRA'), 'America/New_York', 'New York', 'United States', 'Final', 'FINAL_RELEASE', '1647849402208-01775e9c9fb3', 'The last ninety minutes of the tournament. One match, one trophy, the whole world watching New York.', 'USD');
--rollback DELETE FROM events WHERE slug <> '' AND stage <> 'Demo';

--changeset apextick:005-04-layouts context:demo
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT count(*) FROM price_tiers pt JOIN events e ON pt.event_id = e.id WHERE e.slug = 'india-pakistan-group-stage'
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 3500, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 7500, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 14000, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 32000, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'india-pakistan-group-stage';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 3500, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 7500, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 14000, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 32000, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'australia-england-super-8';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 3500, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 7500, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 14000, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 32000, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'south-africa-new-zealand-super-8';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 3500, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 7500, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 14000, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 32000, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'india-australia-semi-final';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 3500, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 7500, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 14000, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 32000, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'world-cup-final';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 2500, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 6000, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 12000, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 25000, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'mumbai-indians-chennai-super-kings';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 2500, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 6000, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 12000, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 25000, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'bengaluru-kolkata-night';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 2500, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 6000, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 12000, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 25000, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'gujarat-titans-rajasthan-royals';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 2500, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 6000, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 12000, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 25000, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'chennai-mumbai-return';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 2500, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 6000, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 12000, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 25000, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'qualifier-one';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 55, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 95, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 160, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 420, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'arsenal-manchester-city';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 55, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 95, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 160, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 420, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'liverpool-manchester-united';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 55, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 95, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 160, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 420, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'manchester-city-tottenham';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 55, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 95, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 160, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 420, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'newcastle-chelsea';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 55, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 95, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 160, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 420, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'aston-villa-arsenal';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 55, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 95, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 160, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 420, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'tottenham-chelsea-derby';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 150, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 320, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 650, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 1800, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'usa-paraguay-group-stage';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 150, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 320, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 650, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 1800, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'brazil-morocco-group-stage';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 150, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 320, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 650, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 1800, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'england-croatia-group-stage';
INSERT INTO price_tiers (event_id, code, name, price, perks, sort_order)
SELECT e.id, v.code, v.name, v.price, v.perks, v.sort_order FROM events e,
  (VALUES ('standard', 'Standard', 150, '["Full match access","General concourse","Open seating zones"]', 0), ('premium', 'Premium', 320, '["Elevated sightlines","Padded seating","Express entry gate"]', 1), ('gold', 'Gold', 650, '["Center stand view","Lounge access","In-seat service"]', 2), ('suite', 'Suite', 1800, '["Private box","Hosted dining","Dedicated concierge"]', 3)) AS v(code, name, price, perks, sort_order)
WHERE e.slug = 'world-cup-final-2026';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'india-pakistan-group-stage';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'australia-england-super-8';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'south-africa-new-zealand-super-8';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'india-australia-semi-final';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'world-cup-final';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'mumbai-indians-chennai-super-kings';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'bengaluru-kolkata-night';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'gujarat-titans-rajasthan-royals';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'chennai-mumbai-return';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'qualifier-one';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'arsenal-manchester-city';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'liverpool-manchester-united';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'manchester-city-tottenham';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'newcastle-chelsea';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'aston-villa-arsenal';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'tottenham-chelsea-derby';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'usa-paraguay-group-stage';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'brazil-morocco-group-stage';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'england-croatia-group-stage';
INSERT INTO sections (event_id, code, name, tier_id, side, row_count, seats_per_row, sort_order)
SELECT e.id, v.code, v.name, (SELECT pt.id FROM price_tiers pt WHERE pt.event_id = e.id AND pt.code = v.tier_code),
       v.side, v.row_count, v.seats_per_row, v.sort_order FROM events e,
  (VALUES ('north', 'North Stand', 'gold', 'n', 4, 18, 0), ('east', 'East Stand', 'premium', 'e', 5, 12, 1), ('south', 'South Stand', 'standard', 's', 6, 18, 2), ('west', 'West Stand', 'premium', 'w', 5, 12, 3)) AS v(code, name, tier_code, side, row_count, seats_per_row, sort_order)
WHERE e.slug = 'world-cup-final-2026';
--rollback DELETE FROM sections WHERE event_id IN (SELECT id FROM events WHERE stage <> 'Demo');

--changeset apextick:005-05-seats context:demo
--preconditions onFail:MARK_RAN onError:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT count(*) FROM seats s JOIN sections sec ON s.section_id = sec.id JOIN events e ON sec.event_id = e.id WHERE e.slug = 'india-pakistan-group-stage'
INSERT INTO seats (event_id, section_id, seat_number, row_idx, col_idx, status, version)
SELECT sec.event_id, sec.id, chr(65 + r) || (c + 1)::text, r, c, 'AVAILABLE', 0
FROM sections sec
CROSS JOIN LATERAL generate_series(0, sec.row_count - 1) AS r
CROSS JOIN LATERAL generate_series(0, sec.seats_per_row - 1) AS c
WHERE NOT EXISTS (SELECT 1 FROM seats s WHERE s.section_id = sec.id);
--rollback DELETE FROM seats WHERE event_id IN (SELECT id FROM events WHERE stage <> 'Demo');
