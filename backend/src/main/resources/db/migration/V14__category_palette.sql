-- Give each subject its own recognisable accent; preserve all content and IDs.
UPDATE categories SET color=CASE name
 WHEN '数学基础' THEN '#c71932'
 WHEN '数学进阶' THEN '#922099'
 WHEN '编程入门' THEN '#21a9d8'
 WHEN '数据结构与算法' THEN '#0ca89d'
 WHEN '软件工程' THEN '#ef1684'
 WHEN '计算机系统基础' THEN '#d97706'
 WHEN '操作系统' THEN '#4169c1'
 WHEN '计算机网络' THEN '#7851a9'
 WHEN '编译原理' THEN '#8a9b30'
 WHEN 'Web开发' THEN '#b85c38'
 WHEN '机器学习' THEN '#087f8c'
 WHEN '深度学习' THEN '#587a96'
 WHEN '数据科学' THEN '#588441'
 ELSE color END
WHERE name IN ('数学基础','数学进阶','编程入门','数据结构与算法','软件工程','计算机系统基础','操作系统','计算机网络','编译原理','Web开发','机器学习','深度学习','数据科学');
