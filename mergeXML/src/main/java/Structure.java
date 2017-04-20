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
    private ArrayList<HashSet<String>> listType;
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
        this.listType = new ArrayList<HashSet<String>>();
        this.xmlInFactory = XMLInputFactory.newFactory();
        this.dir = new File("ub_adapters");
        this.currentFile = new File(new File("").getAbsolutePath());
        //this.outputWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("CacheValidate.xml"), "UTF-8"));
        this.xmlOutFactory = XMLOutputFactory.newFactory();
        //this.xmlEventWriter = xmlOutFactory.createXMLEventWriter(outputWriter);
        this.xmlEventFactory = XMLEventFactory.newFactory();
    }

    /**
     * Создание xml структуры файла валидации
     * @throws Throwable
     */
    void addBaseStructure() throws Throwable{
        Boolean checkFileCache = readBaseFile();

        if (!checkFileCache) return;

        //версия темпового файла с кешом
        tempFileCache = currentFile + "//tmpCacheValidate.xml";
        new File("tmpCacheValidate.xml");

        this.outputWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFileCache), "UTF-8"));
        this.xmlEventWriter = xmlOutFactory.createXMLEventWriter(outputWriter);
        //массив файлов потоков
        File[] flowFiles = dir.listFiles();

        //структура для элементов
        xmlEventWriter.add(xmlEventFactory.createStartDocument());
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "ControlMessage"));
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "ValidateMessage"));
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "DefinitionElement"));
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "ExecutionGroups"));
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "ExecutionGroup"));
        xmlEventWriter.add(xmlEventFactory.createAttribute("name", dir.getName()));
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "Flows"));

        System.out.println("-------Build Cache validation for ESB UB-------");
        System.out.println();

        checkFile(flowFiles);

        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "Flows"));
        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "ExecutionGroup"));
        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "ExecutionGroups"));
        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "DefinitionElement"));

        //структура для типов
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "DefinitionType"));
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "ExecutionGroups"));
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "ExecutionGroup"));
        xmlEventWriter.add(xmlEventFactory.createAttribute("name", dir.getName()));
        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "Flows"));

        //добавляем наименования файлов типов для всех потоков адаптеров
        addIncludeTypeInDefinitionType(flowFiles);

        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "Flows"));
        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "ExecutionGroup"));
        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "ExecutionGroups"));

        xmlEventWriter.add(xmlEventFactory.createStartElement("", null, flowFiles[flowFiles.length - 1].getName()));

        //добавляем типы
        addTypeList(flowFiles[flowFiles.length - 1].listFiles());
        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, flowFiles[flowFiles.length - 1].getName()));

        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "DefinitionType"));
        xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "ValidateMessage"));
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
     * Проверка на файл.
     * Читаем все директории кроме последний папки с типами.
     * @param file
     * @throws XMLStreamException
     */
    private void checkFile(File[] file) throws XMLStreamException {
        for (int i = 0; i < file.length - 1; i++) {
            if ((!file[i].isFile()) && (file[i].getName() != "TypeList")){
                //список наименований схем типов, которые используются в XSD cхемах в данном потоке
                HashSet<String> hashSet = new HashSet<String>();
                xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "Flow"));
                xmlEventWriter.add(xmlEventFactory.createAttribute("name", file[i].getName()));

                System.out.println("Flow " + file[i].getName() + ":");
                addService(file[i].listFiles(), hashSet);

                listType.add(hashSet);
                xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "Flow"));
            }
        }
    }

    /**
     * Добвляем сервис.
     * Добавляем имя сервиса и версию.
     * Вызываем функцию для добавления xsd схемы
     * @param file
     * @param hashSet
     * @throws XMLStreamException
     */
    private void addService(File[] file, HashSet<String> hashSet) throws XMLStreamException {
        for (File rootFile : file) {
            if (!rootFile.isFile()){
                for (File rootFileVer : rootFile.listFiles()) {
                    xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "Service"));
                    xmlEventWriter.add(xmlEventFactory.createAttribute("name", rootFile.getName()));
                    xmlEventWriter.add(xmlEventFactory.createAttribute("version", rootFileVer.getName()));

                    addXSD(rootFileVer.listFiles(), hashSet);

                    xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "Service"));
                    System.out.println("     Added schema: " + rootFile.getName()+ "_v" + rootFileVer.getName());
                }
            }
        }
    }

    /**
     * Добавляем ссылку на файл с типами, которые используются в схеме XSD
     * За тем добавляем саму XSD
     * @param file
     * @param hashSet
     * @throws XMLStreamException
     */
    private void addXSD(File[] file, HashSet<String> hashSet) throws XMLStreamException {
        if (file.length != 0) {
            xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "IncludeTypes"));
            xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "Type"));

            //имя файла с типами
            String includeType = addIncludeType(file);
            hashSet.add(includeType);

            xmlEventWriter.add(xmlEventFactory.createCharacters(includeType));
            xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "Type"));
            xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "IncludeTypes"));

            xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "Xsd"));

            //Добавляем xsd
            for (File rootFile : file) {
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
            }
            xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "Xsd"));
        }
    }

    /**
     * Функция вырезает из текста xsd схемы наименование файла с типами,
     * который к ней подклчен
     * @param file
     * @return - имя файла с типами
     * @throws XMLStreamException
     */
    private String addIncludeType(File[] file) throws XMLStreamException {
        for (File rootFile : file) {
            XMLEventReader xmlEventReader = xmlInFactory.createXMLEventReader(new StreamSource(rootFile));
            XMLEvent event;
            for (int i = 0; i < 15; i++) {
                event = xmlEventReader.nextEvent();
                String strData = event.toString();
                if (strData.contains(".xsd") && strData.contains("/Types/")){
                    xmlEventReader.close();
                    int count = strData.split("/").length;
                    int length = strData.split("/")[count-1].length();
                    String includeType = strData.split("/")[count-1].substring(0,length-6);
                    return includeType;
                }else if (strData.contains(".xsd") && strData.contains("\\Types\\")){
                    xmlEventReader.close();
                    int count = strData.split("\\\\").length;
                    int length = strData.split("\\\\")[count-1].length();
                    String includeType = strData.split("\\\\")[count-1].substring(0,length-6);
                    return includeType;
                }
            }
        }
        return null;
    }

    /**
     * Для каждого потока добавляем список наименований схем с типами.
     * @param file
     * @throws XMLStreamException
     */
    private void addIncludeTypeInDefinitionType(File[] file) throws XMLStreamException {
        for (int i = 0; i < file.length-1; i++) {
            xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "Flow"));
            xmlEventWriter.add(xmlEventFactory.createAttribute("name", file[i].getName()));
            xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "IncludeTypes"));
            for (String s : listType.get(i)) {
                xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "Type"));
                xmlEventWriter.add(xmlEventFactory.createCharacters(s));
                xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "Type"));
            }
            xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "IncludeTypes"));
            xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "Flow"));
        }
    }

    /**
     * Добавляем файлы с типами
     * @param file
     * @throws XMLStreamException
     */
    private void addTypeList(File[] file) throws XMLStreamException {
        for (File rootFile : file) {
            xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "Type"));
            xmlEventWriter.add(xmlEventFactory.createAttribute("name", rootFile.getName()));
            xmlEventWriter.add(xmlEventFactory.createStartElement("", null, "Xsd"));
            for (File schema : rootFile.listFiles()) {

                XMLEventReader xmlEventReader = xmlInFactory.createXMLEventReader(new StreamSource(schema));
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

            }
            xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "Xsd"));
            xmlEventWriter.add(xmlEventFactory.createEndElement("", null, "Type"));
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
            if (baseFile.isFile() && baseFile.getName().startsWith("CacheValidate")){
                baseFileCache = baseFile;
                nameFileCache = baseFile.getName();
                System.out.println(nameFileCache);
               // String[] partNameCacheValidate = nameFileCache.split("v")[1].split(".xml");
            //    versionBase = Double.parseDouble(partNameCacheValidate[0]);
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
            path = "..\\..\\Config\\env\\cache\\VALIDATE";
        }else if (Files.exists(pathESBConfiguration)){
            path = "..\\..\\ESBConfiguration\\Installer\\env\\cache\\VALIDATE";
        }else return false;

        if (hashBase.equals(hashTemp)){
            System.out.println("Схемы не менялись.");
            new File(tempFileCache).delete();
        }else{
            System.out.println("Схемы менялись");
            baseFileCache.delete();
            File cacheFile =  new File(tempFileCache);
            cacheFile.renameTo(baseFileCache);
            new File(path + "\\CacheValidate.xml").delete();
            copyFileUsingStream(baseFileCache,  new File(path + "\\CacheValidate.xml"));
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
