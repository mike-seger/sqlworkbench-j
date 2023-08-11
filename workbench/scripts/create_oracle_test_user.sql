CREATE USER WBJUNIT IDENITIFED BY wbjunit
  default tablespace users
  temporary tablespace temp
  quota unlimited on users;

CREATE USER WBJUNIT2 IDENITIFED BY wbjunit
  default tablespace users
  temporary tablespace temp
  quota unlimited on users;

GRANT CREATE SESSION TO WBJUNIT;
GRANT CREATE TABLE TO WBJUNIT;
GRANT CREATE CLUSTER TO WBJUNIT;
GRANT CREATE SYNONYM TO WBJUNIT;
GRANT CREATE VIEW TO WBJUNIT;
GRANT CREATE SEQUENCE TO WBJUNIT;
GRANT CREATE PROCEDURE TO WBJUNIT;
GRANT CREATE TRIGGER TO WBJUNIT;
GRANT CREATE MATERIALIZED VIEW TO WBJUNIT;
GRANT CREATE TYPE TO WBJUNIT;

GRANT CREATE SESSION TO WBJUNIT2;
GRANT CREATE TABLE TO WBJUNIT2;
GRANT CREATE CLUSTER TO WBJUNIT2;
GRANT CREATE SYNONYM TO WBJUNIT2;
GRANT CREATE VIEW TO WBJUNIT2;
GRANT CREATE SEQUENCE TO WBJUNIT2;
GRANT CREATE PROCEDURE TO WBJUNIT2;
GRANT CREATE TRIGGER TO WBJUNIT2;
GRANT CREATE MATERIALIZED VIEW TO WBJUNIT2;
GRANT CREATE TYPE TO WBJUNIT2;

-- "plustrace" privileges
GRANT SELECT ON SYS.V_$SESSION TO WBJUNIT;
GRANT SELECT ON SYS.V_$SQL TO WBJUNIT;
GRANT SELECT ON SYS.V_$SQLAREA TO WBJUNIT;
GRANT SELECT ON SYS.V_$SQL_PLAN TO WBJUNIT;
GRANT SELECT ON SYS.V_$SQL_PLAN_STATISTICS TO WBJUNIT;
GRANT SELECT ON SYS.V_$SQL_PLAN_STATISTICS_ALL TO WBJUNIT;
GRANT SELECT ON SYS.V_$SQL_WORKAREA TO WBJUNIT;
GRANT SELECT ON SYS.V_$TRANSACTION TO WBJUNIT;
GRANT SELECT ON SYS.V_$PARAMETER TO WBJUNIT;
grant select on SYS.v_$sesstat to wbjunit;
grant select on sys.v_$statname to wbjunit;
grant select on sys.v_$mystat to wbjunit;
