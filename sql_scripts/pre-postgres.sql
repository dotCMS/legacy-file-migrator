-- Create the tracking table
CREATE TABLE file_migration
(
  identifier VARCHAR(36) NOT NULL,
  server_id VARCHAR(36),
  status INT DEFAULT 0,
  errormsg TEXT
);
ALTER TABLE file_migration ADD CONSTRAINT PK_file_migration PRIMARY KEY (identifier);
CREATE INDEX IDX_file_migration_1 ON file_migration (identifier);
CREATE INDEX IDX_file_migration_2 ON file_migration (server_id);

-- Drop file_asset constraints
--
-- IMPORTANT: IN SOME DATASETS FROM CUSTOMERS, THE NAME OF THESE SPECIFIC CONSTRAINTS WAS CHANGED 
-- AT SOME POINT. FOR EXAMPLE, "fk_fileasset_ver_info_ident" IS CALLED "fk_fileasset_version_info_ident" 
-- IN THEIR DATABASE, AND SO ON. IF THAT'S THE CASE, THESE QUERIES NEED TO BE ADJUSTED TO REFLECT 
-- THIS NEW NAME SO THAT THE PLUGIN WORKS AS EXPECTED.
--
ALTER TABLE file_asset DROP CONSTRAINT fk7ed2366d5fb51eb;
ALTER TABLE file_asset DROP CONSTRAINT file_identifier_fk;

ALTER TABLE IF EXISTS fileasset_version_info DROP CONSTRAINT IF EXISTS fk_fileasset_ver_info_working;
ALTER TABLE IF EXISTS fileasset_version_info DROP CONSTRAINT IF EXISTS fk_fileasset_version_info_working;
ALTER TABLE IF EXISTS fileasset_version_info DROP CONSTRAINT IF EXISTS fk_fileasset_ver_info_live;
ALTER TABLE IF EXISTS fileasset_version_info DROP CONSTRAINT IF EXISTS fk_fileasset_version_info_live;
ALTER TABLE IF EXISTS fileasset_version_info DROP CONSTRAINT IF EXISTS fk_fileasset_ver_info_working;
ALTER TABLE IF EXISTS fileasset_version_info DROP CONSTRAINT IF EXISTS fk_fileasset_version_info_working;


-- Get all Legacy Files in the system
INSERT INTO file_migration(identifier) 
	SELECT fa.identifier  
	   FROM file_asset fa 
	INNER JOIN fileasset_version_info vi 
	   ON fa.inode = vi.working_inode 
	INNER JOIN identifier ident 
	   ON asset_type = 'file_asset' AND fa.identifier = ident.id;

-- SP to load Legacy Files to migrate
CREATE OR REPLACE FUNCTION load_legacyfiles_to_migrate(serverId CHARACTER VARYING, records_to_fetch INT, status_level INT)
  RETURNS SETOF file_migration AS'
DECLARE
   fm file_migration;
BEGIN
    FOR fm IN SELECT * FROM file_migration
       WHERE (server_id IS NULL OR server_id = serverId) 
       AND status <= status_level
       ORDER BY status ASC
       LIMIT records_to_fetch
       FOR UPDATE
    LOOP
        UPDATE file_migration SET server_id = serverId WHERE identifier = fm.identifier;
        RETURN NEXT fm;
    END LOOP;
END'
LANGUAGE 'plpgsql';
