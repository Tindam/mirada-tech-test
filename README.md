# Tech test

## Anonymisation Assumptions

Depending on the data required we may want to anonymise more or less. For the sake of this exercise I will be assuming data to be anonymised (excluding date fields) is:
- PatientsName
- OtherPatientNames
- PatientId
- IssuerOfPatientID
- OtherPatientIDsSequence
- PatientsAddress
- OperatorsName
- ReviewerName

I believe I am probably missing some fields too!

Additionally, as part of anonymisation I chose to add the anonymised field for all fields, even if it didn't exist in the existing dicom. It would be a small change to check the presence of each field before setting the new value - but one that may make anonymisation less thorough.

This list could likely be extended dependent on what data is required. For example, I have not removed patient age, sex, ethnicity as this may be required for some processing.

## Data consistency

As the search tool is currently iterating over individual dicoms so will I. I would anticipate from past experience that it may be beneficial to anonymise further IDs in a way that is consistent over a patient / study so they can be processed properly.

In this case this may not be as much of an issue as I am not anonymising many IDs, but in the event that more IDs were anonymised, this would be something I would want to investigate.

## Normalising time

Without further information I will aim to keep time normalisation as simple as possible. Applying the time difference between actual acquisition and 1st Jan 2023 and applying to all dates within the dicom.

For this example I will be assuming the dates:
- StudyDate
- SeriesDate
- AcquisitionDate
- ContentDate
- PatientBirthDate
- DateOfLastCalibration
- InstanceCreationDate

Note: This may not be an appropriate simplification for all dates e.g. date of birth as I am not altering patient date also.

## Saving new Dicom

I am a bit rusty with dicom standard, but I believe SOPInstanceUID should be unique, so I will generate a new UID before saving the new anonymised dicom.


## Logging

To be blunt, I do not think the logging is appropriate but I kept it simple. I would expect:
- Logs that contain values from within the dicom rather than just the filename e.g. "Anonymising StudyInstanceUID xxxxx"
- If required, logs that actually contain the before and after of certain values (particularly if being able to revert the anonymisation is required)

## Other improvements

I am not happy with the handling of the file path - but I didn't want to mess with splitting the path. Ideally I would expect it to at least be a parallel directory!

Tests - I didn't implement any unit tests which I would expect to see.

Documentation - More documentation is never bad, I would add some javadocs to the methods created.