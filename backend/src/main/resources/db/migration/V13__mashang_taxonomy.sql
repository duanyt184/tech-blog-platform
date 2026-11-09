-- Replace the reference forum taxonomy without deleting any topic or draft.
INSERT IGNORE INTO categories(name,description,color) VALUES
 ('数学基础','从逻辑、集合与函数出发，打好微积分、线性代数和概率基础。','#777770'),
 ('数学进阶','深入实分析、抽象代数、优化理论与数学证明。','#777770'),
 ('编程入门','交流语言基础、编程思维、开发环境与入门练习。','#777770'),
 ('数据结构与算法','讨论数据组织、算法设计、复杂度分析与解题方法。','#777770'),
 ('软件工程','分享需求分析、架构设计、测试、版本控制与工程实践。','#777770'),
 ('计算机系统基础','理解数据表示、计算机组成、体系结构与程序运行机制。','#777770'),
 ('操作系统','探索进程、线程、内存管理、文件系统与系统编程。','#777770'),
 ('计算机网络','讨论网络协议、互联网架构、网络编程与排障。','#777770'),
 ('编译原理','研究词法语法分析、类型系统、中间表示与代码生成。','#777770'),
 ('Web开发','交流前后端开发、浏览器、接口设计与 Web 应用实践。','#777770'),
 ('机器学习','讨论模型、特征工程、训练评估与经典学习方法。','#777770'),
 ('深度学习','探索神经网络、模型训练、计算机视觉与自然语言处理。','#777770'),
 ('数据科学','分享数据分析、统计建模、数据处理与可视化实践。','#777770');

-- The old taxonomy had no matching subject for some general discussions;
-- retain those topics under software engineering rather than dropping content.
UPDATE articles SET category=CASE
 WHEN category IN ('前端开发','后端开发') THEN 'Web开发'
 WHEN category='数据库' THEN '数据科学'
 WHEN category IN ('学习笔记','Help','Support','Guides') THEN '编程入门'
 ELSE '软件工程' END,
 updated_at=updated_at
WHERE category NOT IN ('数学基础','数学进阶','编程入门','数据结构与算法','软件工程','计算机系统基础','操作系统','计算机网络','编译原理','Web开发','机器学习','深度学习','数据科学');

UPDATE categories SET parent_id=NULL;
DELETE FROM categories WHERE name NOT IN ('数学基础','数学进阶','编程入门','数据结构与算法','软件工程','计算机系统基础','操作系统','计算机网络','编译原理','Web开发','机器学习','深度学习','数据科学');

-- Retain tags used by existing posts. Replace unused directory entries with
-- ten useful starter tags; authors and admins can still create additional tags.
DELETE FROM forum_tags WHERE name NOT IN (SELECT DISTINCT tag FROM article_tags);
INSERT IGNORE INTO forum_tags(name,description,group_name) VALUES
 ('Python','Python 编程、生态与实践。','语言与工具'),
 ('Java','Java 语言与应用开发。','语言与工具'),
 ('TypeScript','TypeScript 类型与 Web 开发。','语言与工具'),
 ('SQL','关系数据库查询与数据处理。','语言与工具'),
 ('算法题','算法练习、思路分析与题解。','交流与实践'),
 ('学习笔记','整理知识、推导过程与学习心得。','交流与实践'),
 ('项目实践','分享可运行项目与实现经验。','交流与实践'),
 ('概念解析','解释原理、辨析概念与建立理解。','交流与实践'),
 ('求助答疑','提出具体问题并交流解决方法。','交流与实践'),
 ('资源分享','分享课程、书籍、文档与学习工具。','交流与实践');
