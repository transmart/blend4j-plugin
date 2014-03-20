package blend4j.plugin
import com.github.jmchilton.blend4j.galaxy.GalaxyInstance
import com.github.jmchilton.blend4j.galaxy.GalaxyInstanceFactory
import com.github.jmchilton.blend4j.galaxy.LibrariesClient
import com.github.jmchilton.blend4j.galaxy.beans.FileLibraryUpload
import com.github.jmchilton.blend4j.galaxy.beans.Library
import com.github.jmchilton.blend4j.galaxy.beans.LibraryContent
import com.github.jmchilton.blend4j.galaxy.beans.LibraryFolder
import com.sun.jersey.api.client.ClientResponse

class RetrieveDataController  {

    def springSecurityService
    def RetrieveDataService

    def JobExportToGalaxy = {
        def statusOK = RetrieveDataService.saveStatusOfExport(params.nameOfTheExportJob,lastExportName: params.nameOfTheLibrary);
        if(statusOK){
            uploadExportFolderToGalaxy(params.nameOfTheExportJob, params.nameOfTheLibrary );
            RetrieveDataService.updateStatusOfExport(params.nameOfTheExportJob,"Done")
        }else{
            RetrieveDataService.updateStatusOfExport(params.nameOfTheExportJob,"Error")
        }
    }

    def uploadExportFolderToGalaxy(String nameOfTheExportJob, String nameOfTheLibrary){

        //final idOfTheUser = AuthUser.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName()).getId() ;
        final idOfTheUser = springSecurityService.getPrincipal().id;
        System.err.println(idOfTheUser);
        def apiKey = UserDetails.findById(idOfTheUser).getGalaxyKey();
        final String email = UserDetails.findById(idOfTheUser).getMailAddress();
        final String url = grailsApplication.config.com.galaxy.blend4j.galaxyURL;
        final GalaxyInstance galaxyInstance = GalaxyInstanceFactory.get(url, apiKey);
        String tempDir = grailsApplication.config.com.recomdata.plugins.tempFolderDirectory.toString()+"/"+nameOfTheExportJob;

        final Library library = new Library(nameOfTheLibrary+ " - " + email);
        final LibrariesClient client = galaxyInstance.getLibrariesClient();
        final Library persistedLibrary = client.createLibrary(library);
        final LibraryContent rootFolder = client.getRootFolder(persistedLibrary.getId());
        final LibraryFolder folder = new LibraryFolder();
        folder.setName(nameOfTheExportJob)
        folder.setFolderId(rootFolder.getId());

        LibraryFolder resultFolder = client.createFolder(persistedLibrary.getId(), folder);
        assert resultFolder.getName().equals(nameOfTheExportJob);
        assert resultFolder.getId() != null;

        File repoFolder = new File(tempDir);
        File[] listOfFiles = repoFolder.listFiles();

        createFoldersAndFiles(listOfFiles, resultFolder, client, persistedLibrary.getId());
    }

    def createFoldersAndFiles(File[] listOfFiles, LibraryFolder rootFolder, LibrariesClient client, String persistedLibraryId ){
        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) {
                try {
                     File testFile = listOfFiles[i];
                     FileLibraryUpload upload = new FileLibraryUpload();
                     upload.setFolderId(rootFolder.getId());
                     upload.setName(testFile.getName().toString());
                     upload.setFileType("tabular");
                     upload.setFile(testFile);
                     ClientResponse resultFile = client.uploadFile(persistedLibraryId, upload);
                     assert resultFile.getStatus() == 200 : resultFile.getEntity(String.class);
                } catch(final IOException ioException) {
                    throw new RuntimeException(ioException);
                }
            }
            else if (listOfFiles[i].isDirectory())
            {
                System.err.println("Directory " + listOfFiles[i].getName());
                LibraryFolder folder = new LibraryFolder();
                folder.setName(listOfFiles[i].getName().toString())
                folder.setFolderId(rootFolder.getId());

                File[] listOfChildrenFiles = listOfFiles[i].listFiles();

                LibraryFolder resultFolder = client.createFolder(persistedLibraryId, folder);
                assert resultFolder.getName().equals(listOfFiles[i].getName());
                assert resultFolder.getId() != null;

                createFoldersAndFiles(listOfChildrenFiles, resultFolder, client, persistedLibraryId);
            }
        }
    }
}