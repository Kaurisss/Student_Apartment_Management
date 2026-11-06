-- 学生公寓管理系统：完整表结构与示例数据，仅在全新数据库中执行一次。
CREATE DATABASE IF NOT EXISTS dorm_management
  DEFAULT CHARACTER SET utf8mb4;
USE dorm_management;
 
CREATE TABLE `user` (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (username),
  CHECK (role IN ('ADMIN', 'STUDENT'))
) ENGINE=InnoDB;
 
CREATE TABLE student (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  student_no VARCHAR(30) NOT NULL,
  name VARCHAR(50) NOT NULL,
  class_name VARCHAR(50) NOT NULL DEFAULT '',
  phone VARCHAR(30) NOT NULL DEFAULT '',
  PRIMARY KEY (id),
  UNIQUE (user_id),
  UNIQUE (student_no),
  FOREIGN KEY (user_id) REFERENCES `user`(id)
) ENGINE=InnoDB;
 
CREATE TABLE dormitory (
  id BIGINT NOT NULL AUTO_INCREMENT,
  building VARCHAR(50) NOT NULL,
  room_no VARCHAR(20) NOT NULL,
  room_type VARCHAR(20) NOT NULL DEFAULT '4人间',
  PRIMARY KEY (id),
  UNIQUE (building, room_no)
) ENGINE=InnoDB;
 
CREATE TABLE bed (
  id BIGINT NOT NULL AUTO_INCREMENT,
  dormitory_id BIGINT NOT NULL,
  bed_no VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'FREE',
  PRIMARY KEY (id),
  UNIQUE (dormitory_id, bed_no),
  FOREIGN KEY (dormitory_id) REFERENCES dormitory(id),
  CHECK (status IN ('FREE', 'OCCUPIED'))
) ENGINE=InnoDB;
 
CREATE TABLE repair (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  dormitory_id BIGINT,
  location VARCHAR(100) NOT NULL DEFAULT '',
  description VARCHAR(500) NOT NULL,
  completion_note VARCHAR(500),
  satisfaction VARCHAR(20),
  status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
  evaluation VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  FOREIGN KEY (student_id) REFERENCES student(id),
  FOREIGN KEY (dormitory_id) REFERENCES dormitory(id),
  CHECK (satisfaction IN ('SATISFIED', 'DISSATISFIED')),
  CHECK (status IN ('WAITING', 'IN_PROGRESS',
                    'COMPLETED', 'CONFIRMED'))
) ENGINE=InnoDB;
 
CREATE TABLE housing (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  bed_id BIGINT,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  check_in_at DATETIME,
  checkout_requested_at DATETIME,
  check_out_at DATETIME,
  active_bed_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN status IN ('ASSIGNED', 'CHECKED_IN')
          THEN bed_id ELSE NULL END) STORED,
  active_student_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN status IN ('ASSIGNED', 'CHECKED_IN')
          THEN student_id ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE (active_bed_id),
  UNIQUE (active_student_id),
  FOREIGN KEY (student_id) REFERENCES student(id),
  FOREIGN KEY (bed_id) REFERENCES bed(id),
  CHECK (status IN ('PENDING', 'ASSIGNED',
                    'CHECKED_IN', 'CHECKED_OUT')),
  CHECK (status = 'PENDING' OR
    (status IN ('ASSIGNED', 'CHECKED_IN', 'CHECKED_OUT')
     AND bed_id IS NOT NULL))
) ENGINE=InnoDB;
 
INSERT INTO `user` (id, username, password_hash, role) VALUES
  (1, 'admin',
   CONCAT('pbkdf2_sha256$120000$dorm_admin_2026$',
          'Zg2y8rLyp3Da+GGUozPcxMh1mLHYg4w3fi8hwW2oYJ4='),
   'ADMIN'),
  (2, 'student01',
   CONCAT('pbkdf2_sha256$120000$dorm_student_2026$',
          'pJw5OiVV0cPq7RHp5a/Qm96Xb78B2rVyeuKDruLIUHo='),
   'STUDENT');
 
INSERT INTO student (id, user_id, student_no, name) VALUES
  (1, 2, 'S2026001', '示例学生');
 
INSERT INTO dormitory (id, building, room_no) VALUES
  (1, '1号楼', '301');
 
INSERT INTO bed (id, dormitory_id, bed_no, status) VALUES
  (1, 1, '01', 'OCCUPIED'),
  (2, 1, '02', 'FREE');
 
INSERT INTO repair (id, student_id, dormitory_id, location, description, status) VALUES
  (1, 1, 1, '1号楼 301', '宿舍灯具无法正常开启', 'WAITING');
 
INSERT INTO housing (id, student_id, bed_id, status) VALUES
  (1, 1, 1, 'ASSIGNED');

CREATE TABLE visitor (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  student_id BIGINT NOT NULL,
  arrived_at DATETIME NOT NULL,
  left_at DATETIME,
  FOREIGN KEY (student_id) REFERENCES student(id),
  CHECK (left_at IS NULL OR left_at >= arrived_at)
) ENGINE=InnoDB;
