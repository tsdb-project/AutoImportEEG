import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.SCPClient;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

public class AutoScp {

    private String IP;
    private int PORT;
    private String USER;
    private String PASSWORD;

    //    outputSetting
    private String PANEL;
    private String MMXFILE;

    //    Directory
    private String EEGDIRECTORY;
    private String CSVDIRECTORY;
    private String DESTINATION;
    private String FINISHEDFILES;



    public AutoScp(String IP, int PORT, String USER, String PASSWORD, String PANEL, String MMXFILE, String EEGDIRECTORY,
                   String CSVDIRECTORY, String DESTINATION, String FINISHEDFILES){
        this.IP = IP;
        this.PORT = PORT;
        this.USER = USER;
        this.PASSWORD = PASSWORD;
        this.PANEL = PANEL;
        this.MMXFILE = MMXFILE;
        this.EEGDIRECTORY = EEGDIRECTORY;
        this.CSVDIRECTORY = CSVDIRECTORY;
        this.DESTINATION = DESTINATION;
        this.FINISHEDFILES = FINISHEDFILES;
    }



    private void cronJob(int hour, int minute, int second) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, second);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                HashMap<String,String> eegFileList = findEEGFiles(EEGDIRECTORY);
                if(eegFileList.size() != 0){
                    setLog(LocalDateTime.now()+ ": start converting files and send to the Mac");
                    List<File> successCSV  = convertToCSV(eegFileList);
                    sendCSVFiles(successCSV);
                }
            }
        }, cal.getTime(), 24 * 60 * 60 * 1000);
    }

    private Boolean scpSend(String path, long length){
        Connection connection = new Connection(IP,PORT);
        try {
            connection.connect();
            boolean isAuthenticated = connection.authenticateWithPassword(USER,PASSWORD);
            if(!isAuthenticated){
                setLog(LocalDateTime.now()+": fail to connect to mac");
                return false;
            }
            SCPClient scpClient = connection.createSCPClient();
            scpClient.put(path,DESTINATION);
            connection.close();
        }catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static HashMap<String,String> findEEGFiles (String dir){
        File directory = new File(dir);
        File[] files = directory.listFiles();
        LinkedList<File> queueList = new LinkedList<>();
        HashMap<String,String> eegFileList = new HashMap<>();
        for (int i = 0; i <files.length;i++){
            if(files[i].isDirectory()){
                queueList.add(files[i]);
            }
        }

        while (! queueList.isEmpty()){
            File tempDirectory = queueList.removeFirst();
            File[] currectFiles = tempDirectory.listFiles();
            for (int j = 0; j< currectFiles.length;j ++){
                if(currectFiles[j].isDirectory()){
                    queueList.add(currectFiles[j]);
                }else{
//                    change to .eeg
                    if(currectFiles[j].getName().endsWith(".csv")){
                        eegFileList.put(currectFiles[j].getAbsolutePath(), currectFiles[j].getParentFile().getName());
                    }
                }
            }
        }

        return eegFileList;
    };

    private List<File> convertToCSV( HashMap<String,String> renameList){
        List<File> successfulCSV = new ArrayList<>();
        for (String sourceFile : renameList.keySet()){
            String arFileOutput = CSVDIRECTORY + renameList.get(sourceFile) + "ar.csv";
            String noarFileOutput = CSVDIRECTORY + renameList.get(sourceFile) + "noar.csv";
            String arCommand = String.format("PSCLI /panel='%s' /SourceFile='%s' /OutputFile='%s' /MMX='%s' /ExportCSV",PANEL,sourceFile,arFileOutput,MMXFILE);
            String noarCommand = String.format("PSCLI /panel='%s' /SourceFile='%s' /OutputFile='%s' /MMX='%s' /ExportCSV",PANEL,sourceFile,noarFileOutput,MMXFILE);

            System.out.println("currect source file: " + sourceFile);

            try{
                int value = switchRegistry("ar");
                Process process = Runtime.getRuntime().exec(arCommand);
                new RunThread(process.getInputStream(), "INFO").start();
                new RunThread(process.getErrorStream(),"ERR").start();
                value += process.waitFor();

                if(value == 0){
                    successfulCSV.add(new File(arFileOutput));
                    System.out.println(LocalDateTime.now()+": generate ar csv file successful: "+ arFileOutput);
                    value += switchRegistry("noar");
                    process = Runtime.getRuntime().exec(noarCommand);
                    new RunThread(process.getInputStream(), "INFO").start();
                    new RunThread(process.getErrorStream(),"ERR").start();
                    value += process.waitFor();
                    if(value == 0){
                        successfulCSV.add(new File(noarFileOutput));
                        File patientFolder = new File(sourceFile).getParentFile();
                        Boolean deleteValue = deleteDir(patientFolder);
                        if(deleteValue){
                            System.out.println(LocalDateTime.now()+": generate noar csv file successful: "+ noarFileOutput);
                            System.out.println(LocalDateTime.now()+": delete folder successful: "+ patientFolder.getName());
                        }else{
                            setLog(LocalDateTime.now()+": generate both csv file successful, but delete folder failed: "+ patientFolder.getName());
                        }
                    }else{
                        setLog(LocalDateTime.now()+": generate noar csv file failed: "+ noarFileOutput);
                    }

                }else{
                    setLog(LocalDateTime.now()+": generate ar csv file failed: "+ arFileOutput);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        File[] patientFolders = new File(EEGDIRECTORY).listFiles();
        for (File patientFolder : patientFolders){
            if(patientFolder.isDirectory() && patientFolder.list().length == 0){
                patientFolder.delete();
            }
        }

        return successfulCSV;
    }

    private static int switchRegistry(String fileType){
        String value;
        String entry = "HKEY_CURRENT_USER\\Software\\Persyst\\PSMMarker\\Settings";
        String parameter = "_ForceRawTrends";

        if(fileType.equals("ar")){
            value = "0x00000000";
        }else {
            value = "0x00000001";
        }

        try{
            Runtime.getRuntime().exec("reg add " + entry + " /v " + parameter + " /t REG_DWORD /d " + value + " /f");
            setLog(LocalDateTime.now()+": change registry to generate "+ fileType + " successful !" );
            return 1;
        }catch (Exception e){
            setLog(LocalDateTime.now()+": change registry to generate "+ fileType + " failed !" );
            return 0;
        }
    }

    private void sendCSVFiles(List<File> fileList){
        for(File file: fileList){
            //scp
            try {
                setLog(LocalDateTime.now()+"start sending file " + file.getName());
                // create txt to represent finish
                File finishMark = new File(file.getAbsolutePath().replace(".csv",".txt"));
                if(scpSend(file.getAbsolutePath(),file.length()) && scpSend(finishMark.getAbsolutePath(),finishMark.length())){
                    setLog(LocalDateTime.now()+": finish sending file "+ file.getName());
                    finishMark.delete();
                    //move to another dir rather than delete
                    file.renameTo(new File(FINISHEDFILES+file.getName()));
                }
            }catch (Exception ee){
                setLog(LocalDateTime.now()+": sending file failed "+ file.getName());
            }
        }
        System.out.println("finished");
    }

    private static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i=0; i<children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }



    private static void setLog(String log){
        try {
            File writename = new File("d:/filesToMac/log/log.txt");
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(writename, true)));
            out.write(log+"\n");
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String args[]){
        try{
            Properties pps = new Properties();
            InputStream in = new BufferedInputStream(new FileInputStream(
                    new File("config.properties")));
            pps.load(in);
            AutoScp autoScp = new AutoScp(pps.getProperty("IP"),Integer.parseInt(pps.getProperty("PORT")),pps.getProperty("USER"),
                    pps.getProperty("PASSWORD"),pps.getProperty("PANEL"),pps.getProperty("MMXFILE"),pps.getProperty("EEGDIRECTORY"),pps.getProperty("CSVDIRECTORY"),pps.getProperty("DESTINATION"),pps.getProperty("FINISHEDFILES"));
            autoScp.cronJob(12,0,0);
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}

