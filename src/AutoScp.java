import java.io.*;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.SCPClient;

import java.time.LocalDateTime;
import java.util.*;

public class AutoScp {

    static final String IP ="192.168.0.1";
    static final int PORT = 22;
    static final String USER = "J";
    static final String PASSWORD = "admin";
    static final String DESTINATION = "D:/fileFromWin";
    public static void main(String args[]){
        System.out.println("start sending");
        String dir = "d:/filesToMac";
        AutoScp.cronJob(12,0,0,dir);
    }

    private static void cronJob(int shi, int fen, int miao, String dir) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, shi);
        cal.set(Calendar.MINUTE, fen);
        cal.set(Calendar.SECOND, miao);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                if(! filesInFolder(dir).isEmpty()){
                    List<FileBean> csvs = filesInFolder(dir);
                    for(FileBean c: csvs){
                        //scp
                        try {
                            setLog(LocalDateTime.now()+"start sending file " + c.getName());
                            // create txt to represent finish
                            File finishMark = new File(c.getDirectory()+c.getName().replace(".csv",".txt"));
                            if(scpSend(c.getDirectory()+c.getName(),c.getBytes()) && scpSend(c.getDirectory()+finishMark.getName(),finishMark.length())){
                                setLog(LocalDateTime.now()+"finish sending file "+ c.getName());
                                finishMark.delete();
                                File currnt = new File(c.getDirectory()+c.getName());
                                currnt.delete();
                            }
                        }catch (Exception ee){
                            setLog(LocalDateTime.now()+"sending file failed "+ c.getName());
                        }
                    }
                    System.out.println("finished");
                }
            }
        }, cal.getTime(), 24 * 60 * 60 * 1000);
    }

    private static Boolean scpSend(String path, long length){
        Connection connection = new Connection(IP,PORT);
        try {
            connection.connect();
            boolean isAuthenticated = connection.authenticateWithPassword(USER,PASSWORD);
            if(!isAuthenticated){
                setLog(LocalDateTime.now()+": fail to connect to mac");
                return false;
            }
            SCPClient scpClient = new SCPClient(connection);
            scpClient.put(path,length,DESTINATION,"0060");
            connection.close();
        }catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private static List<FileBean> filesInFolder(String directory) {
        File folder = new File(directory);

        if (!directory.endsWith("/")) {
            directory += "/";
        }

        File[] listOfFiles = folder.listFiles();
        List<FileBean> fileBeans = new ArrayList<>();

        if (listOfFiles != null) {
            for (File listOfFile : listOfFiles) {
                if (listOfFile.isFile() && listOfFile.getName().substring(listOfFile.getName().lastIndexOf(".")).toLowerCase().equals(".csv")) {
                    FileBean fileBean = new FileBean();
                    fileBean.setName(listOfFile.getName());
                    fileBean.setDirectory(directory);
                    long length = listOfFile.length();
                    fileBean.setBytes(length);
                    fileBeans.add(fileBean);
                }
            }
        }
        return fileBeans;
    }

    private static void setLog(String log){
        try {
            File writename = new File("d:fileToMac/log/log.txt");
            writename.createNewFile();
            BufferedWriter out = new BufferedWriter(new FileWriter(writename));
            out.write(log+"\n");
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
