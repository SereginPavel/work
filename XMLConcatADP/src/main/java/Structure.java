import javax.xml.stream.*;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Created by SerP on 20.11.2016.
 */
public class Structure {

    //region параметры вывода и xml структуры
    private XMLInputFactory xmlInFactory;
    private File dir;
    private File currentFile;
    private File baseFileCache;
    private Writer outputWriter;
    private XMLOutputFactory xmlOutFactory;
    private XMLEventWriter xmlEventWriter;
    private XMLEventFactory xmlEventFactory;
    private String tempFileCache; //путь к темповому файлу с кешом
    //endregion

    public Structure() throws Throwable{
        this.xmlInFactory = XMLInputFactory.newFactory();
        this.dir = new File("ub_adapters");
        this.currentFile = new File(new File("").getAbsolutePath());
        this.xmlOutFactory = XMLOutputFactory.newFactory();
        this.xmlEventFactory = XMLEventFactory.newFactory();
    }

    /**
     * Создание xml структуры файла кеша
     * @throws Throwable
     */
    void addBaseStructure() throws Throwable{
        Boolean checkFileCache = readBaseFile();

        if (!checkFileCache) return;

        //версия темпового файла с кешом
        tempFileCache = currentFile + "//tmpCacheAdapter.xml";
        new File("tmpCacheAdapter.xml");

        this.outputWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFileCache), "UTF-8"));
        this.xmlEventWriter = xmlOutFactory.createXMLEventWriter(outputWriter);
        //массив файлов потоков
        File[] flowFiles = dir.listFiles();

        //структура для элементов
        xmlEventWriter.add(xmlEventFactory.createStartDocument());
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "ControlMessage"));
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "ExecutionGroups"));
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "ExecutionGroup"));
        xmlEventWriter.add(xmlEventFactory.createAttribute("name", dir.getName()));
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, flowFiles[0].getName()));

        System.out.println("-------Build Cache validation for ESB UB-------");
        System.out.println();

        checkFile(flowFiles[0].listFiles());

        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "Flows"));
        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "ExecutionGroup"));
        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "ExecutionGroups"));
        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "ControlMessage"));
        xmlEventWriter.add(xmlEventFactory.createEndDocument());

        xmlEventWriter.close();
        outputWriter.close();
        System.out.println();

        //проверка хэшей файлов
        checkCacheFiles(baseFileCache.getName(), tempFileCache);

        System.out.println("SUCCESSFULLY!!!");
    }

    /**
     * считываем потоки
     * @param file
     * @throws XMLStreamException
     */
    private void checkFile(File[] file) throws XMLStreamException {
        for (int i = 0; i < file.length; i++) {
                //список наименований схем типов, которые используются в XSD cхемах в данном потоке
                //HashSet<String> hashSet = new HashSet<String>();
                xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "Flow"));
                xmlEventWriter.add(xmlEventFactory.createAttribute("name", file[i].getName()));

                System.out.println("Flow " + file[i].getName() + ":");
                addAdapter(file[i].listFiles());

                //listType.add(hashSet);
                xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "Flow"));
        }
    }

    /**
     * Добвляем адаптер.
     * @param file
     * @throws XMLStreamException
     */
    private void addAdapter(File[] file) throws XMLStreamException {
        for (File rootFile : file) {
            if (rootFile.isFile()){
                XMLEventReader xmlEventReader = xmlInFactory.createXMLEventReader(new StreamSource(rootFile));
                XMLEvent event = xmlEventReader.nextEvent();
                // Skip ahead in the input to the opening document element
                while (event.getEventType() != XMLEvent.START_ELEMENT) {
                    event = xmlEventReader.nextEvent();
                }

                do {
                    xmlEventWriter.add(event);
                    event = xmlEventReader.nextEvent();
                } while (event.getEventType() != XMLEvent.END_DOCUMENT);
                xmlEventReader.close();
                System.out.println("     Added schema: " + rootFile.getName());
            }
        }
    }

    /**
     * Функция определяет версию базового кеша
     * @return
     */
    Boolean readBaseFile(){
        //имя файла кеша
        String nameFileCache;
        File[] baseDir = currentFile.listFiles();
        for (File baseFile: baseDir) {
            if (baseFile.isFile() && baseFile.getName().startsWith("CacheAdapter")){
                baseFileCache = baseFile;
                nameFileCache = baseFile.getName();
                System.out.println(nameFileCache);
           //     String[] partNameCacheValidate = nameFileCache.split("v")[1].split(".xml");
           //     versionBase = Double.parseDouble(partNameCacheValidate[0]);
                return true;
            }
        }
        System.out.println("Не найден файл кеша");
        return false;
    }

    /**
     * Функция определяет хеш файла
     * @param fileName - имя файла
     * @return
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    String getHashFile(String fileName) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        FileInputStream fis = new FileInputStream(fileName);
        byte[] dataBytes = new byte[1024];
        int nread = 0;
        while((nread = fis.read(dataBytes)) != -1){
            md.update(dataBytes, 0, nread);
        }
        byte[] mdbytes = md.digest();

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < mdbytes.length; i++) {
            sb.append(Integer.toString((mdbytes[i] & 0xff), 16).substring(1));
        }
        fis.close();
        System.out.println("Хэш код файла " + fileName + " " + sb);
        return sb.toString();
    }

    /**
     * Функция сравнивает хеши базового и сгенерированного файлов
     * @param baseFileName - имя базового файла
     * @param tempFileName - имя темпового файла
     * @return
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    Boolean checkCacheFiles(String baseFileName, String tempFileName) throws IOException, NoSuchAlgorithmException {
        String hashBase = getHashFile(baseFileName);
        String hashTemp = getHashFile(tempFileName);
        Path pathConfig = Paths.get("..\\..\\Config");
        Path pathESBConfiguration = Paths.get("..\\..\\ESBConfiguration");
        String path="";

        //проверка существования папки с конфигом
        if (Files.exists(pathConfig)){
            path = "..\\..\\Config\\env\\cache\\ADP";
        }else if (Files.exists(pathESBConfiguration)){
            path = "..\\..\\ESBConfiguration\\Installer\\env\\cache\\ADP";
        }else return false;

        if (hashBase.equals(hashTemp)){
            System.out.println("Схемы не менялись.");
            new File(tempFileCache).delete();
        }else{
            System.out.println("Схемы менялись");
            baseFileCache.delete();
            File cacheFile =  new File(tempFileCache);
            cacheFile.renameTo(baseFileCache);
            new File(path + "\\CacheAdapter.xml").delete();
            copyFileUsingStream(baseFileCache,  new File(path + "\\CacheAdapter.xml"));
        }
        return false;
    }

    private void copyFileUsingStream(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
        }
    }
}
