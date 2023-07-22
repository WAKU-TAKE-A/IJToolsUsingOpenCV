import java.io.File;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.features2d.AKAZE;
import org.opencv.features2d.BRISK;
import org.opencv.features2d.ORB;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/*
 * The MIT License
 *
 * Copyright 2018 nishida.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/**
 * Instead of FeatureDetector
 * @author nishida
 */
public class MyFeatureDetector {
    // const var.
    private final int TYPE_ELEMENT = 1;

    private final String STR_AKAZE = "AKAZE";
    private final String STR_BRISK = "BRISK";
    private final String STR_ORB = "ORB";

    // var.
    private AKAZE m_akaze = null;
    private BRISK m_brisk = null;
    private ORB m_orb = null;

    private String detType = "";
    private Boolean isCreate = false;
    private DocumentBuilderFactory factory = null;
    private DocumentBuilder builder = null;
    private Document document = null;

    // for BRISK
    private static int brisk_thresh = 30;
    private static int brisk_octaves = 3;
    private static float brisk_patternScale = 1.0f;

    // consructor
    public MyFeatureDetector(String type) {
        detType = type;
        brisk_thresh = 30;
        brisk_octaves = 3;
        brisk_patternScale = 1.0f;
    }

    // public method
    public String getDetectorType() {
        return detType;
    }

    public void create() {
        brisk_thresh = 30;
        brisk_octaves = 3;
        brisk_patternScale = 1.0f;

        if(detType.equals(STR_AKAZE)) {
            m_akaze =  AKAZE.create();
        }
        else if(detType.equals(STR_BRISK)) {
            m_brisk =  BRISK.create();
        }
        else if(detType.equals(STR_ORB)) {
            m_orb =  ORB.create();
        }

        isCreate = true;
    }

    public void readParam(String fname) throws SAXException, IOException, ParserConfigurationException {
        if(!isCreate || fname == null || fname.isEmpty()) {
            return;
        }

        try {
            Element root = readXml(fname);
            String root_name = root.getNodeName();

            if(!detType.equals(root_name)) {
                throw new IllegalArgumentException();
            }

            NodeList nodeList = root.getChildNodes();
            int num = nodeList.getLength();

            for(int i = 0; i < num; i++) {
                Node node = nodeList.item(i);
                String node_name = node.getNodeName();
                String node_value = node.getTextContent();
                int type = node.getNodeType();

                if(type == TYPE_ELEMENT) {
                    //
                    // AKAZE
                    //
                    if(detType.equals(STR_AKAZE) && node_name.equals("DescriptorChannels")) {
                        m_akaze.setDescriptorChannels(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_AKAZE) && node_name.equals("DescriptorSize")) {
                        m_akaze.setDescriptorSize(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_AKAZE) && node_name.equals("DescriptorType")) {
                        m_akaze.setDescriptorType(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_AKAZE) && node_name.equals("Diffusivity")) {
                        m_akaze.setDiffusivity(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_AKAZE) && node_name.equals("NOctaveLayers")) {
                        m_akaze.setNOctaveLayers(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_AKAZE) && node_name.equals("NOctaves")) {
                        m_akaze.setNOctaves(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_AKAZE) && node_name.equals("Threshold")) {
                        m_akaze.setThreshold(Double.parseDouble(node_value));
                    }
                    //
                    // BRISK
                    //
                    else if(detType.equals(STR_BRISK) && node_name.equals("Thresh")) {
                        brisk_thresh =  Integer.parseInt(node_value);
                    }
                    else if(detType.equals(STR_BRISK) && node_name.equals("Octaves")) {
                        brisk_octaves =  Integer.parseInt(node_value);
                    }
                    else if(detType.equals(STR_BRISK) && node_name.equals("PatternScale")) {
                        brisk_patternScale =  Float.parseFloat(node_value);
                    }
                    //
                    // ORB
                    //
                    else if(detType.equals(STR_ORB) && node_name.equals("MaxFeatures")) {
                        m_orb.setMaxFeatures(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_ORB) && node_name.equals("ScaleFactor")) {
                        m_orb.setScaleFactor(Double.parseDouble(node_value));
                    }
                    else if(detType.equals(STR_ORB) && node_name.equals("NLevels")) {
                        m_orb.setNLevels(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_ORB) && node_name.equals("EdgeThreshold")) {
                        m_orb.setEdgeThreshold(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_ORB) && node_name.equals("FirstLevel")) {
                        m_orb.setFirstLevel(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_ORB) && node_name.equals("WTA_K")) {
                        m_orb.setWTA_K(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_ORB) && node_name.equals("ScoreType")) {
                        m_orb.setScoreType(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_ORB) && node_name.equals("PatchSize")) {
                        m_orb.setPatchSize(Integer.parseInt(node_value));
                    }
                    else if(detType.equals(STR_ORB) && node_name.equals("FastThreshold")) {
                        m_orb.setFastThreshold(Integer.parseInt(node_value));
                    }
                }
            }

            if(detType.equals(STR_BRISK)) {
                detType = STR_BRISK;
                m_brisk = BRISK.create(brisk_thresh, brisk_octaves, brisk_patternScale);
                isCreate = true;
            }
        }
        catch(ParserConfigurationException | SAXException | IOException ex) {
            throw ex;
        }
    }

    public void writeDefalutParam(String fname) throws TransformerException, ParserConfigurationException {
        if(!isCreate ||  fname == null || fname.isEmpty()) {
            return;
        }

        try {
            factory = DocumentBuilderFactory.newInstance();
            builder = factory.newDocumentBuilder();
            document = builder.newDocument();
            Element root = document.createElement(detType);
            document.appendChild(root);

            if(detType.equals(STR_AKAZE)) {
                addToRoot(root, "DescriptorChannels", String.valueOf(m_akaze.getDescriptorChannels()));
                addToRoot(root, "DescriptorSize", String.valueOf(m_akaze.getDescriptorSize()));
                addToRoot(root, "DescriptorType", String.valueOf(m_akaze.getDescriptorType()));
                addToRoot(root, "Diffusivity", String.valueOf(m_akaze.getDiffusivity()));
                addToRoot(root, "NOctaveLayers", String.valueOf(m_akaze.getNOctaveLayers()));
                addToRoot(root, "NOctaves", String.valueOf(m_akaze.getNOctaves()));
                addToRoot(root, "Threshold", String.valueOf(m_akaze.getThreshold()));
            }
            else if(detType.equals(STR_BRISK)) {
                addToRoot(root, "Thresh", String.valueOf(brisk_thresh));
                addToRoot(root, "Octaves", String.valueOf(brisk_octaves));
                addToRoot(root, "PatternScale", String.valueOf(brisk_patternScale));
            }
            else if(detType.equals(STR_ORB)) {
                addToRoot(root, "MaxFeatures", String.valueOf(m_orb.getMaxFeatures()));
                addToRoot(root, "ScaleFactor", String.valueOf(m_orb.getScaleFactor()));
                addToRoot(root, "NLevels", String.valueOf(m_orb.getNLevels()));
                addToRoot(root, "EdgeThreshold", String.valueOf(m_orb.getEdgeThreshold()));
                addToRoot(root, "FirstLevel", String.valueOf(m_orb.getFirstLevel()));
                addToRoot(root, "WTA_K", String.valueOf(m_orb.getWTA_K()));
                addToRoot(root, "ScoreType", String.valueOf(m_orb.getScoreType()));
                addToRoot(root, "PatchSize", String.valueOf(m_orb.getPatchSize()));
                addToRoot(root, "FastThreshold", String.valueOf(m_orb.getFastThreshold()));
            }

            writeXml(fname, document);
        }
        catch(TransformerException | ParserConfigurationException ex) {
            throw ex;
        }
    }

    public void detect(Mat img_query, MatOfKeyPoint key_query) {
        if(detType.isEmpty()) {
            return;
        }

        if(detType.equals(STR_AKAZE)) {
            m_akaze.detect(img_query, key_query);
        }
        else if(detType.equals(STR_BRISK)) {
            m_brisk.detect(img_query, key_query);
        }
        else if(detType.equals(STR_ORB)) {
            m_orb.detect(img_query, key_query);
        }
    }

    public void compute(Mat img_query, MatOfKeyPoint key_query, Mat desc_query) {
        if(detType.isEmpty()) {
            return;
        }

        if(detType.equals(STR_AKAZE)) {
            m_akaze.compute(img_query, key_query, desc_query);
        }
        else if(detType.equals(STR_BRISK)) {
            m_brisk.compute(img_query, key_query, desc_query);
        }
        else if(detType.equals(STR_ORB)) {
            m_orb.compute(img_query, key_query, desc_query);
        }
    }

    // private method
    private Element readXml(String fname) throws ParserConfigurationException, SAXException, IOException {
        try {
            factory = DocumentBuilderFactory.newInstance();
            builder = factory.newDocumentBuilder();
            document = builder.parse(fname);
            Element root = document.getDocumentElement();
            return root;
        }
        catch(ParserConfigurationException | SAXException | IOException ex) {
            throw ex;
        }
    }

    private void addToRoot(Element root, String name, String value) {
        Element child_one = document.createElement(name);
        child_one.appendChild(document.createTextNode(value));
        root.appendChild(child_one);
    }

    private void writeXml(String fname, Document document) throws TransformerConfigurationException, TransformerException {
        try {
            File file = new File(fname);

            // Transformerインスタンスの生成
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();

            // Transformerの設定
            transformer.setOutputProperty("indent", "yes"); //改行指定
            transformer.setOutputProperty("encoding", "utf-8"); // エンコーディング

            // XMLファイルの作成
            transformer.transform(new DOMSource(document), new StreamResult(file));
        }
        catch(TransformerConfigurationException e) {
            throw e;
        }
        catch(TransformerException ex) {
            throw ex;
        }
    }
}
