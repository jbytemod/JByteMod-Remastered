package de.xbrowniecodez.jbytemod.utils.apk;

import pxb.android.axml.AxmlParser;
import pxb.android.axml.Axml;
import pxb.android.axml.AxmlReader;
import pxb.android.axml.AxmlWriter;
import pxb.android.axml.NodeVisitor;
import pxb.android.axml.ValueWrapper;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public final class AndroidBinaryXmlDecoder {
    private static final int MAX_DEPTH = 512;
    private static final int MAX_NODES = 100_000;
    private static final int MAX_OUTPUT_CHARS = 20 * 1024 * 1024;
    private static final String ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android";
    private static final String[] DIMENSION_UNITS = {"px", "dp", "sp", "pt", "in", "mm"};
    private static final String[] FRACTION_UNITS = {"%", "%p"};
    private static final Pattern COMPLEX_VALUE = Pattern.compile(
            "^([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))(px|dp|sp|pt|in|mm|%p|%)$");
    private static final float[] RADIX_MULTS = {
            1.0f / (1L << 31), 1.0f / (1 << 24), 1.0f / (1 << 16), 1.0f / (1 << 8)
    };

    private AndroidBinaryXmlDecoder() {
    }

    public static boolean isBinaryXml(byte[] bytes) {
        return bytes != null && bytes.length >= 8
                && bytes[0] == 0x03 && bytes[1] == 0x00
                && bytes[2] == 0x08 && bytes[3] == 0x00;
    }

    public static String decode(byte[] bytes) throws IOException {
        if (!isBinaryXml(bytes)) {
            throw new IOException("Resource does not have an Android binary XML header.");
        }

        AxmlParser parser = new AxmlParser(bytes);
        NamespaceTable namespaces = new NamespaceTable();
        List<XmlNode> roots = new ArrayList<>();
        Deque<XmlNode> stack = new ArrayDeque<>();
        int nodeCount = 0;

        while (true) {
            int event = parser.next();
            if (event == AxmlParser.END_FILE) break;
            switch (event) {
                case AxmlParser.START_FILE -> {
                }
                case AxmlParser.START_NS -> namespaces.register(
                        parser.getNamespacePrefix(), parser.getNamespaceUri());
                case AxmlParser.START_TAG -> {
                    if (++nodeCount > MAX_NODES) {
                        throw new IOException("Android XML contains too many elements.");
                    }
                    if (stack.size() >= MAX_DEPTH) {
                        throw new IOException("Android XML nesting is too deep.");
                    }
                    XmlNode node = new XmlNode(parser.getNamespaceUri(), requireName(parser.getName()));
                    namespaces.ensure(node.namespace());
                    for (int index = 0; index < parser.getAttrCount(); index++) {
                        String namespace = parser.getAttrNs(index);
                        namespaces.ensure(namespace);
                        node.attributes().add(new XmlAttribute(namespace,
                                requireName(parser.getAttrName(index)),
                                formatValue(parser.getAttrType(index), parser.getAttrValue(index))));
                    }
                    if (stack.isEmpty()) roots.add(node);
                    else stack.peek().content().add(node);
                    stack.push(node);
                }
                case AxmlParser.TEXT -> {
                    if (!stack.isEmpty() && parser.getText() != null) {
                        stack.peek().content().add(parser.getText());
                    }
                }
                case AxmlParser.END_TAG -> {
                    if (stack.isEmpty()) throw new IOException("Android XML has an unmatched closing element.");
                    stack.pop();
                }
                case AxmlParser.END_NS -> {
                }
                default -> throw new IOException("Unsupported Android XML event: " + event);
            }
        }
        if (!stack.isEmpty() || roots.isEmpty()) {
            throw new IOException(stack.isEmpty() ? "Android XML has no root element."
                    : "Android XML ended before all elements were closed.");
        }

        StringBuilder output = new StringBuilder(Math.min(bytes.length * 2, 1_000_000));
        output.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        for (XmlNode root : roots) {
            appendNode(output, root, namespaces, 0, true);
            if (output.length() > MAX_OUTPUT_CHARS) {
                throw new IOException("Decoded Android XML is too large to display.");
            }
        }
        return output.toString();
    }

    public static byte[] encode(String xml, byte[] originalBytes) throws IOException {
        if (xml == null || xml.length() > MAX_OUTPUT_CHARS) {
            throw new IOException("Edited Android XML is too large.");
        }
        if (!isBinaryXml(originalBytes)) {
            throw new IOException("The original resource is not Android binary XML.");
        }

        Axml original = new Axml();
        new AxmlReader(originalBytes).accept(original);
        Document document = parseDocument(xml);
        Element root = document.getDocumentElement();
        if (root == null) throw new IOException("Android XML has no root element.");

        Axml.Node originalRoot = findOriginalRoot(original.firsts, root);
        AxmlWriter writer = new AxmlWriter();
        registerNamespaces(writer, document);
        int[] nodeCount = {0};
        NodeVisitor rootVisitor = writer.child(namespace(root), localName(root));
        writeElement(rootVisitor, root, originalRoot, 0, nodeCount);
        writer.end();

        byte[] encoded = writer.toByteArray();
        validateEncodedXml(encoded);
        return encoded;
    }

    private static Document parseDocument(String xml) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IllegalArgumentException exception) {
            throw new IOException("Edited Android XML is invalid: " + exception.getMessage(), exception);
        }
    }

    private static void registerNamespaces(AxmlWriter writer, Document document) {
        Set<String> registeredUris = new HashSet<>();
        NodeList elements = document.getElementsByTagName("*");
        for (int elementIndex = 0; elementIndex < elements.getLength(); elementIndex++) {
            NamedNodeMap attributes = elements.item(elementIndex).getAttributes();
            for (int attributeIndex = 0; attributeIndex < attributes.getLength(); attributeIndex++) {
                Attr attribute = (Attr) attributes.item(attributeIndex);
                if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) continue;
                String uri = attribute.getValue();
                if (uri.isBlank() || !registeredUris.add(uri)) continue;
                String prefix = XMLConstants.XMLNS_ATTRIBUTE.equals(attribute.getName())
                        ? null : attribute.getLocalName();
                writer.ns(prefix, uri, 1);
            }
        }
    }

    private static void writeElement(NodeVisitor visitor, Element element, Axml.Node original,
                                     int depth, int[] nodeCount) throws IOException {
        if (++nodeCount[0] > MAX_NODES) throw new IOException("Edited Android XML contains too many elements.");
        if (depth >= MAX_DEPTH) throw new IOException("Edited Android XML nesting is too deep.");
        visitor.line(original != null && original.ln != null ? original.ln : 1);

        Map<AttributeKey, Axml.Node.Attr> originalAttributes = new LinkedHashMap<>();
        if (original != null) {
            for (Axml.Node.Attr attribute : original.attrs) {
                originalAttributes.put(new AttributeKey(attribute.ns, attribute.name), attribute);
            }
        }
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Attr attribute = (Attr) attributes.item(index);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) continue;
            String namespace = namespace(attribute);
            String name = localName(attribute);
            Axml.Node.Attr originalAttribute = originalAttributes.get(new AttributeKey(namespace, name));
            EncodedValue encoded = encodeValue(attribute.getValue(), originalAttribute);
            visitor.attr(namespace, name,
                    originalAttribute == null ? -1 : originalAttribute.resourceId,
                    encoded.type(), encoded.value());
        }

        List<Element> childElements = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            org.w3c.dom.Node child = children.item(index);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                childElements.add((Element) child);
            } else if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE
                    || child.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                text.append(child.getNodeValue());
            }
        }
        if (!childElements.isEmpty() && !text.toString().isBlank()) {
            throw new IOException("Mixed text and child elements are not supported in Android binary XML: "
                    + element.getTagName());
        }
        if (childElements.isEmpty() && !text.isEmpty()) {
            visitor.text(original != null && original.text != null ? original.text.ln : 1, text.toString());
        }

        List<Axml.Node> originalChildren = original == null ? List.of() : original.children;
        boolean[] used = new boolean[originalChildren.size()];
        for (int index = 0; index < childElements.size(); index++) {
            Element child = childElements.get(index);
            Axml.Node originalChild = findOriginalChild(originalChildren, used, child, index);
            NodeVisitor childVisitor = visitor.child(namespace(child), localName(child));
            writeElement(childVisitor, child, originalChild, depth + 1, nodeCount);
        }
        visitor.end();
    }

    private static Axml.Node findOriginalRoot(List<Axml.Node> roots, Element element) {
        for (Axml.Node root : roots) {
            if (sameElement(root, element)) return root;
        }
        return null;
    }

    private static Axml.Node findOriginalChild(List<Axml.Node> children, boolean[] used,
                                               Element element, int preferredIndex) {
        if (preferredIndex < children.size() && !used[preferredIndex]
                && sameElement(children.get(preferredIndex), element)) {
            used[preferredIndex] = true;
            return children.get(preferredIndex);
        }
        for (int index = 0; index < children.size(); index++) {
            if (!used[index] && sameElement(children.get(index), element)) {
                used[index] = true;
                return children.get(index);
            }
        }
        return null;
    }

    private static boolean sameElement(Axml.Node node, Element element) {
        return Objects.equals(node.ns, namespace(element)) && Objects.equals(node.name, localName(element));
    }

    private static EncodedValue encodeValue(String text, Axml.Node.Attr original) throws IOException {
        if (original != null && text.equals(formatValue(original.type, original.value))) {
            return new EncodedValue(original.type, original.value);
        }
        if (original != null) {
            return new EncodedValue(original.type, parseTypedValue(original.type, text, original.value));
        }
        return inferValue(text);
    }

    private static Object parseTypedValue(int type, String text, Object originalValue) throws IOException {
        try {
            Object value = switch (type) {
                case 0x00 -> {
                    if (!"@null".equals(text)) throw new IllegalArgumentException("expected @null");
                    yield 0;
                }
                case 0x01, 0x07 -> parseReference(text, '@');
                case 0x02, 0x08 -> parseReference(text, '?');
                case 0x03 -> text;
                case 0x04 -> finiteFloat(text);
                case 0x05 -> parseComplex(text, false);
                case 0x06 -> parseComplex(text, true);
                case 0x10 -> Integer.parseInt(text);
                case 0x11 -> parseHex(text);
                case 0x12 -> parseBoolean(text);
                case 0x1c, 0x1d, 0x1e, 0x1f -> parseColor(text);
                default -> parseHex(text);
            };
            if (originalValue instanceof ValueWrapper wrapper) {
                if (!(value instanceof Integer reference)) {
                    throw new IllegalArgumentException("expected a numeric resource reference");
                }
                return switch (wrapper.type) {
                    case ValueWrapper.ID -> ValueWrapper.wrapId(reference, text);
                    case ValueWrapper.STYLE -> ValueWrapper.wrapStyle(reference, text);
                    case ValueWrapper.CLASS -> ValueWrapper.wrapClass(reference, text);
                    default -> throw new IllegalArgumentException("unsupported wrapped value");
                };
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Cannot encode value '" + text + "' as Android type 0x"
                    + Integer.toHexString(type) + ": " + exception.getMessage(), exception);
        }
    }

    private static EncodedValue inferValue(String text) throws IOException {
        try {
            if ("@null".equals(text)) return new EncodedValue(0x00, 0);
            if (text.matches("@0[xX][0-9a-fA-F]{1,8}")) return new EncodedValue(0x01, parseReference(text, '@'));
            if (text.matches("\\?0[xX][0-9a-fA-F]{1,8}")) return new EncodedValue(0x02, parseReference(text, '?'));
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                return new EncodedValue(0x12, Boolean.parseBoolean(text));
            }
            if (text.matches("#[0-9a-fA-F]{8}")) return new EncodedValue(0x1c, parseColor(text));
            if (text.matches("#[0-9a-fA-F]{6}")) return new EncodedValue(0x1d, parseColor(text));
            if (text.matches("#[0-9a-fA-F]{4}")) return new EncodedValue(0x1e, parseColor(text));
            if (text.matches("#[0-9a-fA-F]{3}")) return new EncodedValue(0x1f, parseColor(text));
            Matcher complex = COMPLEX_VALUE.matcher(text);
            if (complex.matches()) {
                boolean fraction = complex.group(2).startsWith("%");
                return new EncodedValue(fraction ? 0x06 : 0x05, parseComplex(text, fraction));
            }
            if (text.matches("[-+]?\\d+")) return new EncodedValue(0x10, Integer.parseInt(text));
            if (text.matches("0[xX][0-9a-fA-F]{1,8}")) return new EncodedValue(0x11, parseHex(text));
            if (text.matches("[-+]?(?:\\d+\\.\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")) {
                return new EncodedValue(0x04, finiteFloat(text));
            }
            if (text.startsWith("@") || text.startsWith("?")) {
                throw new IllegalArgumentException("symbolic references cannot be resolved; use @0x... or ?0x...");
            }
            return new EncodedValue(0x03, text);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Cannot infer an Android binary type for value '" + text + "': "
                    + exception.getMessage(), exception);
        }
    }

    private static int parseReference(String text, char prefix) {
        if (prefix == '@' && "@null".equals(text)) return 0;
        if (text.length() < 4 || text.charAt(0) != prefix
                || text.charAt(1) != '0' || Character.toLowerCase(text.charAt(2)) != 'x') {
            throw new IllegalArgumentException("symbolic references cannot be resolved; use " + prefix + "0x...");
        }
        return parseUnsignedHex(text.substring(3));
    }

    private static int parseHex(String text) {
        String value = text.startsWith("0x") || text.startsWith("0X") ? text.substring(2) : text;
        return parseUnsignedHex(value);
    }

    private static int parseColor(String text) {
        if (!text.startsWith("#")) throw new IllegalArgumentException("expected # followed by hexadecimal digits");
        return parseUnsignedHex(text.substring(1));
    }

    private static int parseUnsignedHex(String text) {
        if (text.isEmpty() || text.length() > 8) throw new IllegalArgumentException("invalid 32-bit hexadecimal value");
        long value = Long.parseUnsignedLong(text, 16);
        if (value > 0xffffffffL) throw new IllegalArgumentException("hexadecimal value exceeds 32 bits");
        return (int) value;
    }

    private static Boolean parseBoolean(String text) {
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) return true;
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) return false;
        throw new IllegalArgumentException("expected true or false");
    }

    private static Float finiteFloat(String text) {
        float value = Float.parseFloat(text);
        if (!Float.isFinite(value)) throw new IllegalArgumentException("value must be finite");
        return value;
    }

    private static int parseComplex(String text, boolean fraction) {
        Matcher matcher = COMPLEX_VALUE.matcher(text);
        if (!matcher.matches()) throw new IllegalArgumentException("invalid complex value");
        String unitName = matcher.group(2);
        if (fraction != unitName.startsWith("%")) throw new IllegalArgumentException("unexpected unit " + unitName);
        String[] units = fraction ? FRACTION_UNITS : DIMENSION_UNITS;
        int unit = -1;
        for (int index = 0; index < units.length; index++) {
            if (units[index].equals(unitName)) {
                unit = index;
                break;
            }
        }
        if (unit < 0) throw new IllegalArgumentException("unsupported unit " + unitName);
        float value = finiteFloat(matcher.group(1));
        if (fraction) value /= 100f;
        return encodeComplex(value, unit);
    }

    private static int encodeComplex(float value, int unit) {
        for (int radix = 0; radix < RADIX_MULTS.length; radix++) {
            double scaled = value / RADIX_MULTS[radix] / 256d;
            if (scaled >= -8_388_608d && scaled <= 8_388_607d) {
                long mantissa = Math.round(scaled);
                return ((int) mantissa << 8) | radix << 4 | unit;
            }
        }
        throw new IllegalArgumentException("value is outside the Android complex-value range");
    }

    private static String namespace(org.w3c.dom.Node node) {
        String namespace = node.getNamespaceURI();
        return namespace == null || namespace.isBlank() ? null : namespace;
    }

    private static String localName(org.w3c.dom.Node node) {
        String localName = node.getLocalName();
        return localName == null ? node.getNodeName() : localName;
    }

    private static void validateEncodedXml(byte[] bytes) throws IOException {
        AxmlParser parser = new AxmlParser(bytes);
        int events = 0;
        while (parser.next() != AxmlParser.END_FILE) {
            if (++events > MAX_NODES * 3) throw new IOException("Encoded Android XML produced too many events.");
        }
    }

    private static void appendNode(StringBuilder output, XmlNode node, NamespaceTable namespaces,
                                   int depth, boolean declareNamespaces) throws IOException {
        indent(output, depth).append('<').append(namespaces.qualify(node.namespace(), node.name()));
        if (declareNamespaces) {
            for (Map.Entry<String, String> namespace : namespaces.entries()) {
                output.append('\n');
                indent(output, depth + 1).append("xmlns:").append(namespace.getValue()).append("=\"");
                appendEscaped(output, namespace.getKey(), true);
                output.append('"');
            }
        }
        for (XmlAttribute attribute : node.attributes()) {
            output.append('\n');
            indent(output, depth + 1).append(namespaces.qualify(attribute.namespace(), attribute.name()))
                    .append("=\"");
            appendEscaped(output, attribute.value(), true);
            output.append('"');
        }
        if (node.content().isEmpty()) {
            output.append(" />\n");
            return;
        }

        boolean textOnly = node.content().stream().allMatch(String.class::isInstance);
        output.append('>');
        if (!textOnly) output.append('\n');
        for (Object item : node.content()) {
            if (item instanceof XmlNode child) {
                appendNode(output, child, namespaces, depth + 1, false);
            } else if (item instanceof String text) {
                if (!textOnly) indent(output, depth + 1);
                appendEscaped(output, text, false);
                if (!textOnly) output.append('\n');
            }
            if (output.length() > MAX_OUTPUT_CHARS) {
                throw new IOException("Decoded Android XML is too large to display.");
            }
        }
        if (!textOnly) indent(output, depth);
        output.append("</").append(namespaces.qualify(node.namespace(), node.name())).append(">\n");
    }

    private static String formatValue(int type, Object value) {
        if (value instanceof String text) return text;
        if (value instanceof Boolean bool) return Boolean.toString(bool);
        int number;
        if (value instanceof ValueWrapper wrapper) {
            if (wrapper.raw != null) return wrapper.raw;
            number = wrapper.ref;
        } else if (value instanceof Number numeric) {
            number = numeric.intValue();
        } else {
            return String.valueOf(value);
        }

        return switch (type) {
            case 0x00 -> number == 0 ? "@null" : String.format(Locale.ROOT, "0x%08x", number);
            case 0x01, 0x07 -> number == 0 ? "@null" : String.format(Locale.ROOT, "@0x%08x", number);
            case 0x02, 0x08 -> String.format(Locale.ROOT, "?0x%08x", number);
            case 0x04 -> Float.toString(Float.intBitsToFloat(number));
            case 0x05 -> complex(number, DIMENSION_UNITS, false);
            case 0x06 -> complex(number, FRACTION_UNITS, true);
            case 0x10 -> Integer.toString(number);
            case 0x11 -> String.format(Locale.ROOT, "0x%08x", number);
            case 0x12 -> Boolean.toString(number != 0);
            case 0x1c -> String.format(Locale.ROOT, "#%08x", number);
            case 0x1d -> String.format(Locale.ROOT, "#%06x", number & 0x00ffffff);
            case 0x1e -> String.format(Locale.ROOT, "#%04x", number & 0x0000ffff);
            case 0x1f -> String.format(Locale.ROOT, "#%03x", number & 0x00000fff);
            default -> String.format(Locale.ROOT, "0x%08x", number);
        };
    }

    private static String complex(int value, String[] units, boolean percentage) {
        int unit = value & 0xf;
        if (unit >= units.length) return String.format(Locale.ROOT, "0x%08x", value);
        float decoded = (value & 0xffffff00) * RADIX_MULTS[(value >> 4) & 3];
        if (percentage) decoded *= 100f;
        return Float.toString(decoded) + units[unit];
    }

    private static String requireName(String name) throws IOException {
        if (name == null || name.isBlank()) throw new IOException("Android XML contains an unnamed element or attribute.");
        return name;
    }

    private static void appendEscaped(StringBuilder output, String value, boolean attribute) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            switch (codePoint) {
                case '&' -> output.append("&amp;");
                case '<' -> output.append("&lt;");
                case '>' -> output.append("&gt;");
                case '"' -> output.append(attribute ? "&quot;" : "\"");
                default -> {
                    if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t'
                            || codePoint >= 0x20 && codePoint <= 0xd7ff
                            || codePoint >= 0xe000 && codePoint <= 0xfffd
                            || codePoint >= 0x10000 && codePoint <= 0x10ffff) {
                        output.appendCodePoint(codePoint);
                    } else {
                        output.append('\ufffd');
                    }
                }
            }
        }
    }

    private static StringBuilder indent(StringBuilder output, int depth) {
        return output.append("  ".repeat(depth));
    }

    private record XmlAttribute(String namespace, String name, String value) {
    }

    private record AttributeKey(String namespace, String name) {
    }

    private record EncodedValue(int type, Object value) {
    }

    private record XmlNode(String namespace, String name, List<XmlAttribute> attributes, List<Object> content) {
        private XmlNode(String namespace, String name) {
            this(namespace, name, new ArrayList<>(), new ArrayList<>());
        }
    }

    private static final class NamespaceTable {
        private final Map<String, String> prefixes = new LinkedHashMap<>();
        private final Set<String> usedPrefixes = new HashSet<>();

        private void register(String prefix, String uri) {
            if (uri == null || uri.isBlank() || prefixes.containsKey(uri)) return;
            String candidate = prefix == null || prefix.isBlank()
                    ? (ANDROID_NAMESPACE.equals(uri) ? "android" : "ns") : prefix;
            candidate = candidate.replaceAll("[^A-Za-z0-9_.-]", "_");
            if (candidate.isBlank() || !Character.isLetter(candidate.charAt(0)) && candidate.charAt(0) != '_') {
                candidate = "ns";
            }
            String unique = candidate;
            for (int suffix = 2; usedPrefixes.contains(unique); suffix++) unique = candidate + suffix;
            prefixes.put(uri, unique);
            usedPrefixes.add(unique);
        }

        private void ensure(String uri) {
            register(null, uri);
        }

        private String qualify(String uri, String name) {
            if (uri == null || uri.isBlank()) return name;
            ensure(uri);
            return prefixes.get(uri) + ':' + name;
        }

        private Set<Map.Entry<String, String>> entries() {
            return prefixes.entrySet();
        }
    }
}
