/**
 * <b>Public API</b> of FAuction for importing the data of another auction house plugin.
 * <p>
 * This package (with its {@code event} and {@code testing} sub-packages) is the only part of
 * FAuction whose compatibility is guaranteed across versions ; everything else is internal.
 * <p>
 * Writing an import module for a plugin X comes down to reading the data of X and turning each row
 * into one of the {@code Imported*} records, pushed into the {@link fr.florianpal.fauction.api.importer.ImportSink}.
 * The validation, the writes to the database, the protection against duplicates, the threads, the
 * report and the command all stay on the FAuction side :
 * <ol>
 *     <li>implement {@link fr.florianpal.fauction.api.importer.DataImporter} (or extend
 *     {@link fr.florianpal.fauction.api.importer.AbstractSqlImporter} /
 *     {@link fr.florianpal.fauction.api.importer.AbstractYamlImporter}) ;</li>
 *     <li>register it in {@link fr.florianpal.fauction.api.importer.ImporterRegistry}, obtained from the
 *     Bukkit {@code ServicesManager} ;</li>
 *     <li>the administrator runs {@code /ah admin import run <id>}.</li>
 * </ol>
 * Modules are tested without FAuction nor a database through
 * {@link fr.florianpal.fauction.api.importer.testing.RecordingImportSink}.
 * <p>
 * Imports require a shared SQL database (MySQL, MariaDB or PostgreSQL) : FAuction refuses to run one
 * in SQLite mode.
 *
 * @see fr.florianpal.fauction.api.importer.ImporterApi#API_VERSION
 */
package fr.florianpal.fauction.api.importer;
