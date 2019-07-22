public class FileBean {
    private String name;
    private String directory;
    private long bytes;
    /**
     * @return the name
     */
    public String getName() {
        return name;
    }
    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }
    /**
     * @return the directory
     */
    public String getDirectory() {
        return directory;
    }
    /**
     * @param directory the directory to set
     */
    public void setDirectory(String directory) {
        this.directory = directory;
    }
    /**
     * @return the bytes
     */
    public long getBytes() {
        return bytes;
    }
    /**
     * @param bytes the bytes to set
     */
    public void setBytes(long bytes) {
        this.bytes = bytes;
    }
}
