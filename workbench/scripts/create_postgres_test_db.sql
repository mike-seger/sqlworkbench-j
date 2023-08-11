 -- superuser is required for the EventTrigger test
create user wbjunit password 'wbjunit'
    with superuser;
create database wbjunit
   encoding = 'UTF8'
   owner = wbjunit;

