package datasearch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class DicomProcessHelpers {

    private static final Logger LOGGER = LogManager.getLogger(DicomProcessHelpers.class);


    public static void saveDicom(String filePath, Attributes fileMetaInformation, Attributes dataset) {
        File newDicom = new File(filePath);
        DicomOutputStream dicomOutputStream;
        try {
            dicomOutputStream = new DicomOutputStream(newDicom);
            dicomOutputStream.writeDataset(fileMetaInformation, dataset);

            dicomOutputStream.flush();
            dicomOutputStream.close();
        } catch (IOException e) {
            LOGGER.error("Error occurred when writing back dicom", e);
        }
    }

    public static void anonymise(Attributes attributes) {
        attributes.setString(Tag.PatientName, VR.PN, "ANON");
        attributes.setString(Tag.OtherPatientNames, VR.PN, "ANON");
        attributes.setString(Tag.PatientID, VR.LO, "ANON");
        attributes.setString(Tag.IssuerOfPatientID, VR.LO, "ANON");

        // Just remove additional patient IDs
        attributes.remove(Tag.OtherPatientIDsSequence);

        attributes.setString(Tag.PersonAddress, VR.ST, "ANON");
        attributes.setString(Tag.PatientAddress, VR.ST, "ANON");

        attributes.setString(Tag.OperatorsName, VR.PN, "ANON");
        attributes.setString(Tag.ReviewerName, VR.PN, "ANON");
        attributes.setString(Tag.SecondaryReviewerName, VR.PN, "ANON");

        attributes.removePrivateAttributes();
    }

    private static final Date normalDate = Date.from(
            LocalDateTime.of(
                    2023,
                    1,
                    1,
                    12,
                    0,
                    0).toInstant(ZoneOffset.UTC));

    private static final List<Integer> dateTags = Arrays.asList(
            Tag.StudyDate,
            Tag.SeriesDate,
            Tag.AcquisitionDate,
            Tag.ContentDate,
            Tag.PatientBirthDate,
            Tag.DateOfLastCalibration,
            Tag.InstanceCreationDate
    );

    public static void normaliseDates(Attributes attributes) {
        Date acquisitionDate = attributes.getDate(Tag.AcquisitionDate);
        long diffInMillis = normalDate.getTime() - acquisitionDate.getTime();

        for (Integer dateTag : dateTags) {
            Date realDate = attributes.getDate(dateTag);
            if (realDate != null) {
                Date normalisedDate = new Date(realDate.getTime() + diffInMillis);

                attributes.setDate(dateTag, VR.DT, normalisedDate);
            }
        }
    }

    public static void replaceInstanceUid(Attributes attributes) {
        attributes.setString(Tag.SOPInstanceUID, VR.UI, String.valueOf(UUID.randomUUID()));
    }
}
