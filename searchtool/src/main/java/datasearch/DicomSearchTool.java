/*
 * Copyright © 2020 Mirada Medical Ltd.
 * All Rights Reserved.
 */
package datasearch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.FileNameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;

/**
 * Main entry point for the search tool
 */
public final class DicomSearchTool
{
   private static final InputStream PATHS_CONFIG = DicomSearchTool.class.getResourceAsStream( "/scraperPaths.csv" ); //$NON-NLS-1$
   private static final Logger LOGGER = LogManager.getLogger( DicomSearchTool.class );

   /**
    * @param args none
    *
    */
   public static void main( String[] args )
   {
      List< String > listOfPaths = readListOfPaths();
      try
      {
         listOfPaths.forEach( path -> walk( path ) );
      }
      catch ( Exception exception )
      {
         LOGGER.error( "Exception thrown while running: ", exception ); //$NON-NLS-1$
      }
      System.out.println( "Scrape Complete" ); //$NON-NLS-1$
   }

   private static List< String > readListOfPaths()
   {
      List< String > paths = new ArrayList<>();

      try
      {
         Scanner scanner = new Scanner( PATHS_CONFIG );
         scanner.useDelimiter( "\n" ); //$NON-NLS-1$
         while ( scanner.hasNext() )
         {
            String line = scanner.next();
            if ( !line.startsWith( "**" ) )
            {
               paths.add( line.replace( "\r", "" ) );
            }
         }

         scanner.close();
      }
      catch ( Exception exception )
      {
         LOGGER.error( "Exception through while getting scraper paths", exception ); //$NON-NLS-1$
      }

      return paths;
   }

   private static void walk( String path )
   {
      Path rootDirPath = Paths.get( path );
      // cannot walk with #recycle in scraperList
      if (  Files.isDirectory( rootDirPath )
            && rootDirPath.getFileName() != null
            && rootDirPath.getFileName().toString().equals( "#recycle" ) ) //$NON-NLS-1$
      {
         return;
      }
      try
      {
         Files.walkFileTree( rootDirPath, new SimpleFileVisitor< Path >()
         {
            @Override
            public FileVisitResult visitFile( Path file, BasicFileAttributes attrs )
               throws IOException
            {
               if ( file.getFileName() != null && file.getFileName().toString().equals( "#recycle" ) ) //$NON-NLS-1$
               {
                  return FileVisitResult.SKIP_SUBTREE;
               }
               String extension = FileNameUtils.getExtension( file.toString().toLowerCase() );
               if ( ( extension.equals( "dcm" ) || ( Files.isRegularFile( file ) && extension.isEmpty() ) ) ) //$NON-NLS-1$
               {
                  // Assumes file without an extension is a DICOM file
                  System.out.println( file );
                  process( file.toString() );
               }
               else if ( extension.equals( "zip" ) || extension.equals( "7z" ) ) //$NON-NLS-1$ //$NON-NLS-2$
               {
                  archiveSearch( file.toString() );
               }
               return super.visitFile( rootDirPath, attrs );
            }

            @Override
            public FileVisitResult visitFileFailed( Path file, IOException exc ) throws IOException
            {
               LOGGER.fatal( exc.getMessage() );

               return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult preVisitDirectory( Path dir, BasicFileAttributes attrs )
            {
               System.out.println( "Visiting directory: " + dir ); //$NON-NLS-1$
               if ( dir.getFileName() != null && "#recycle".equals( dir.getFileName().toString() ) ) //$NON-NLS-1$
               {
                  return FileVisitResult.SKIP_SUBTREE;
               }
               return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory( Path dir, IOException exc )
               throws IOException
            {
               System.out.println( "Directory visited: " + dir ); //$NON-NLS-1$
               LOGGER.info( "Directory visited: " + dir ); //$NON-NLS-1$

               return super.postVisitDirectory( rootDirPath, exc );
            }
         } );
      }
      catch ( IOException exception )
      {
         LOGGER.error( String.format( "Could not read directory %s", rootDirPath ), exception ); //$NON-NLS-1$
      }
   }

   /*
    * Process dicom object
    */
   private static void process( String fileName ) throws IOException
   {
      Attributes fmi;
      Attributes dataset;

      try ( DicomInputStream dis = new DicomInputStream( new File( fileName ) ) )
      {
         dis.setIncludeBulkData( IncludeBulkData.URI );
         fmi = dis.readFileMetaInformation();
         dataset = dis.readDataset();
      }

      if ( fmi != null )
      {
         fmi = dataset.createFileMetaInformation( fmi.getString( Tag.TransferSyntaxUID ) );
      }

      // TODO: process the data set
   }

   public static void archiveSearch( String file )
   {
      System.out.println( "Visiting archive: " + file ); //$NON-NLS-1$
      String extension = FileNameUtils.getExtension( file.toLowerCase() );
      try
      {
         if ( "zip".equals( extension ) ) //$NON-NLS-1$
         {
            ZipFile zipfile = new ZipFile( file );
            zipSearch( zipfile, file );
            zipfile.close();
         }
         else
         {
            File archiveFile = new File( file );
            archivableSearch( archiveFile, file );
         }
      }
      catch ( Exception exception )
      {
         LOGGER.error( "Unknown error encountered in: " + file, exception ); //$NON-NLS-1$
      }
   }

   private static void zipSearch( ZipFile zipfile, String file )
   {
      Enumeration< ZipArchiveEntry > zipEntries = zipfile.getEntries();

      while ( zipEntries.hasMoreElements() )
      {
         ZipArchiveEntry entry = zipEntries.nextElement();
         try ( InputStream stream = zipfile.getInputStream( entry ); )
         {
            entrySearch( entry, stream, file );
         }
         catch ( IOException exception )
         {
            LOGGER.error( "Error viewing zip entry: ", exception ); //$NON-NLS-1$
         }
      }
      try
      {
         zipfile.close();
      }
      catch ( IOException exception )
      {
         LOGGER.error( "Unable to close zip file " + file, exception ); //$NON-NLS-1$
         if ( exception.getMessage().contains( "An unexpected network error occurred" ) ) //$NON-NLS-1$
         {
            LOGGER.fatal( file + ": " + exception.getMessage() ); //$NON-NLS-1$
         }
      }
      catch ( Exception exception )
      {
         LOGGER.fatal( file + ": " + exception.getMessage() ); //$NON-NLS-1$
      }
   }

   private static void archivableSearch( File file, String filepath )
   {
      try ( SevenZFile zip = new SevenZFile( file ); )
      {
         if ( file.toString().toLowerCase().endsWith( ".7z" ) ) //$NON-NLS-1$
         {
            Iterable< SevenZArchiveEntry > entries = zip.getEntries();

            for ( SevenZArchiveEntry entry : entries )
            {
               try ( InputStream stream = zip.getInputStream( entry ); )
               {
                  entrySearch( entry, stream, filepath );
               }
               catch ( IOException exception )
               {
                  LOGGER.error( "Error viewing zip entry: ", exception ); //$NON-NLS-1$
               }
            }
            zip.close();
         }
      }
      catch ( IOException exception )
      {
         LOGGER.error( "Error searching archivable file " + filepath, exception ); //$NON-NLS-1$
         if ( exception.getMessage().contains( "An unexpected network error occurred" ) ) //$NON-NLS-1$
         {
            LOGGER.fatal( file + ": " + exception.getMessage() ); //$NON-NLS-1$
         }
      }
      catch ( Exception exception )
      {
         LOGGER.fatal( file + ": " + exception.getMessage() ); //$NON-NLS-1$
      }
   }

   private static void entrySearch( ArchiveEntry entry, InputStream stream, String file ) throws IOException
   {
      String filepath = file + "\\" + entry.getName().replace( '/', '\\' ); //$NON-NLS-1$

      if ( ( entry.getName().toLowerCase().endsWith( ".dcm" ) //$NON-NLS-1$
             || ( !entry.isDirectory() && FileNameUtils.getExtension( entry.getName().toString() ).isEmpty() ) ) )
      {
         System.out.println( filepath );
      }
      else if ( entry.getName().toLowerCase().endsWith( ".zip" ) //$NON-NLS-1$
                || entry.getName().toLowerCase().endsWith( ".7z" ) ) //$NON-NLS-1$
      {
         try
         {
            String extension = entry.getName().toLowerCase().substring( entry.getName().lastIndexOf( "." ) ); //$NON-NLS-1$
            String prefix = entry.getName().replaceAll( "[^A-Za-z0-9]", "" ); //$NON-NLS-1$ //$NON-NLS-2$

            // Write temporary file and search file for any DICOM files
            Path tempPath =  Files.createTempFile( prefix, extension );
            FileOutputStream outputStream = new FileOutputStream( tempPath.toFile() );
            stream.transferTo( outputStream );

            if ( ".zip".equals( extension ) ) //$NON-NLS-1$
            {
               ZipFile zipfile = new ZipFile( tempPath.toString() );
               zipSearch( zipfile, filepath );
               zipfile.close();
            }
            else
            {
               archivableSearch( new File( tempPath.toString() ), filepath );
            }

            stream.close();
            outputStream.close();
            Files.delete( tempPath );
         }
         catch ( IOException exception )
         {
            LOGGER.error( "Error trying to access archive file: " + filepath, exception ); //$NON-NLS-1$
         }
      }
   }
}
