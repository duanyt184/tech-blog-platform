INSERT IGNORE INTO categories(name,description,color) VALUES
 ('Events / Meetups','Meet other builders. Find community gatherings and share upcoming events.','#922099'),
 ('Account & Billing','Questions and discussion about accounts, subscriptions, and billing. Do not post private account information.','#ed1766');
UPDATE categories SET color='#c71932' WHERE name='Announcements' AND color='#e9a441';
UPDATE categories SET color='#21a9d8' WHERE name='Discussions' AND color='#6d9e6f';
UPDATE categories SET color='#0ca89d' WHERE name='Support' AND color='#6893c5';
UPDATE categories SET color='#ef1684' WHERE name='Ideas' AND color='#bb88b9';
UPDATE categories SET color='#80847d' WHERE name='Guides' AND color='#56a89c';
INSERT IGNORE INTO categories(name,description,color,parent_id)
 SELECT 'Feature Requests','Suggest new features and improvements.','#9bbd30',id FROM categories WHERE name='Ideas';
INSERT IGNORE INTO categories(name,description,color,parent_id)
 SELECT 'Feedback','Share constructive feedback about your experience.','#127aad',id FROM categories WHERE name='Ideas';
INSERT IGNORE INTO categories(name,description,color,parent_id)
 SELECT 'Release Discussions','Discuss new releases and changes.','#0ca89d',id FROM categories WHERE name='Announcements';
