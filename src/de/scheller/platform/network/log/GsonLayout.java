/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package de.scheller.platform.network.log;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.impl.MementoMessage;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.util.KeyValuePair;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;

import com.cedarsoftware.util.io.JsonWriter;
import com.cedarsoftware.util.io.JsonWriter.JsonClassWriter;

import de.scheller.platform.common.X;
import de.scheller.transferobject.ITransferObject;

/**
 * Appends a series of JSON events as strings serialized as bytes.
 *
 * <h3>Complete well-formed JSON vs. fragment JSON</h3>
 * <p>
 * If you configure {@code complete="true"}, the appender outputs a well-formed JSON document. By default, with
 * {@code complete="false"}, you should include the output as an <em>external file</em> in a separate file to form a
 * well-formed JSON document.
 * </p>
 * <p>
 * If {@code complete="false"}, the appender does not write the JSON open array character "[" at the start
 * of the document, "]" and the end, nor comma "," between records.
 * </p>
 * <h3>Encoding</h3>
 * <p>
 * Appenders using this layout should have their {@code charset} set to {@code UTF-8} or {@code UTF-16}, otherwise
 * events containing non ASCII characters could result in corrupted log files.
 * </p>
 * <h3>Pretty vs. compact JSON</h3>
 * <p>
 * By default, the JSON layout is not compact (a.k.a. "pretty") with {@code compact="false"}, which means the
 * appender uses end-of-line characters and indents lines to format the text. If {@code compact="true"}, then no
 * end-of-line or indentation is used. Message content may contain, of course, escaped end-of-lines.
 * </p>
 * <h3>Additional Fields</h3>
 * <p>
 * This property allows addition of custom fields into generated JSON.
 * {@code <JsonLayout><KeyValuePair key="foo" value="bar"/></JsonLayout>} inserts {@code "foo":"bar"} directly
 * into JSON output. Supports Lookup expressions.
 * </p>
 */
@Plugin(name = "GsonLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public final class GsonLayout extends AbstractJacksonLayout {

	private static final String DEFAULT_FOOTER = "]";

	private static final String DEFAULT_HEADER = "[";

	static final String CONTENT_TYPE = "application/json";

	public static class Builder<B extends Builder<B>> extends AbstractJacksonLayout.Builder<B>
			implements org.apache.logging.log4j.core.util.Builder<GsonLayout> {

		@PluginBuilderAttribute
		private boolean propertiesAsList;

		@PluginBuilderAttribute
		private boolean objectMessageAsJsonObject;

		@PluginElement("AdditionalField")
		private KeyValuePair[] additionalFields;

		public Builder() {
			super();
			setCharset(StandardCharsets.UTF_8);
		}

		@Override
		public GsonLayout build() {
			final boolean encodeThreadContextAsList = isProperties() && propertiesAsList;
			final String headerPattern = toStringOrNull(getHeader());
			final String footerPattern = toStringOrNull(getFooter());
			return new GsonLayout(getConfiguration(), isLocationInfo(), isProperties(), encodeThreadContextAsList,
					isComplete(), isCompact(), getEventEol(), getEndOfLine(), headerPattern, footerPattern, getCharset(),
					isIncludeStacktrace(), isStacktraceAsString(), isIncludeNullDelimiter(), isIncludeTimeMillis(),
					getAdditionalFields(), getObjectMessageAsJsonObject());
		}

		public boolean isPropertiesAsList() {
			return propertiesAsList;
		}

		public B setPropertiesAsList(final boolean propertiesAsList) {
			this.propertiesAsList = propertiesAsList;
			return asBuilder();
		}

		public boolean getObjectMessageAsJsonObject() {
			return objectMessageAsJsonObject;
		}

		public B setObjectMessageAsJsonObject(final boolean objectMessageAsJsonObject) {
			this.objectMessageAsJsonObject = objectMessageAsJsonObject;
			return asBuilder();
		}

		@Override
		public KeyValuePair[] getAdditionalFields() {
			return additionalFields;
		}

		@Override
		public B setAdditionalFields(final KeyValuePair[] additionalFields) {
			this.additionalFields = additionalFields;
			return asBuilder();
		}
	}

	/**
	 * @deprecated Use {@link #newBuilder()} instead
	 */
	@Deprecated
	protected GsonLayout(final Configuration config, final boolean locationInfo, final boolean properties,
			final boolean encodeThreadContextAsList,
			final boolean complete, final boolean compact, final boolean eventEol, final String endOfLine,final String headerPattern,
			final String footerPattern, final Charset charset, final boolean includeStacktrace) {
		super(config, gson(),
				charset, compact, complete, eventEol, endOfLine,
				PatternLayout.newSerializerBuilder().setConfiguration(config).setPattern(headerPattern).setDefaultPattern(DEFAULT_HEADER).build(),
				PatternLayout.newSerializerBuilder().setConfiguration(config).setPattern(footerPattern).setDefaultPattern(DEFAULT_FOOTER).build(),
				false, null);
	}

	private GsonLayout(final Configuration config, final boolean locationInfo, final boolean properties,
					final boolean encodeThreadContextAsList,
					final boolean complete, final boolean compact, final boolean eventEol, final String endOfLine,
					final String headerPattern, final String footerPattern, final Charset charset,
					final boolean includeStacktrace, final boolean stacktraceAsString,
					final boolean includeNullDelimiter, final boolean includeTimeMillis,
					final KeyValuePair[] additionalFields, final boolean objectMessageAsJsonObject) {
		super(config, gson(),
//        		new JacksonFactory.JSON(encodeThreadContextAsList, includeStacktrace, stacktraceAsString, objectMessageAsJsonObject)
//        		.newWriter(locationInfo, properties, compact, includeTimeMillis),
				charset, compact, complete, eventEol, endOfLine,
				PatternLayout.newSerializerBuilder().setConfiguration(config).setPattern(headerPattern).setDefaultPattern(DEFAULT_HEADER).build(),
				PatternLayout.newSerializerBuilder().setConfiguration(config).setPattern(footerPattern).setDefaultPattern(DEFAULT_FOOTER).build(),
				includeNullDelimiter,
				additionalFields);
	}

	/**
	 * Returns appropriate JSON header.
	 *
	 * @return a byte array containing the header, opening the JSON array.
	 */
	@Override
	public byte[] getHeader() {
		if (!this.complete) {
			return null;
		}
		final StringBuilder buf = new StringBuilder();
		final String str = serializeToString(getHeaderSerializer());
		if (str != null) {
			buf.append(str);
		}
		buf.append(this.eol);
		return getBytes(buf.toString());
	}

	/**
	 * Returns appropriate JSON footer.
	 *
	 * @return a byte array containing the footer, closing the JSON array.
	 */
	@Override
	public byte[] getFooter() {
		if (!this.complete) {
			return null;
		}
		final StringBuilder buf = new StringBuilder();
		buf.append(this.eol);
		final String str = serializeToString(getFooterSerializer());
		if (str != null) {
			buf.append(str);
		}
		buf.append(this.eol);
		return getBytes(buf.toString());
	}

	@Override
	public Map<String, String> getContentFormat() {
		final Map<String, String> result = new HashMap<>();
		result.put("version", "2.0");
		return result;
	}

	/**
	 * @return The content type.
	 */
	@Override
	public String getContentType() {
		return CONTENT_TYPE + "; charset=" + this.getCharset();
	}

	/**
	 * Creates a JSON Layout.
	 * @param config
	 *           The plugin configuration.
	 * @param locationInfo
	 *            If "true", includes the location information in the generated JSON.
	 * @param properties
	 *            If "true", includes the thread context map in the generated JSON.
	 * @param propertiesAsList
	 *            If true, the thread context map is included as a list of map entry objects, where each entry has
	 *            a "key" attribute (whose value is the key) and a "value" attribute (whose value is the value).
	 *            Defaults to false, in which case the thread context map is included as a simple map of key-value
	 *            pairs.
	 * @param complete
	 *            If "true", includes the JSON header and footer, and comma between records.
	 * @param compact
	 *            If "true", does not use end-of-lines and indentation, defaults to "false".
	 * @param eventEol
	 *            If "true", forces an EOL after each log event (even if compact is "true"), defaults to "false". This
	 *            allows one even per line, even in compact mode.
	 * @param headerPattern
	 *            The header pattern, defaults to {@code "["} if null.
	 * @param footerPattern
	 *            The header pattern, defaults to {@code "]"} if null.
	 * @param charset
	 *            The character set to use, if {@code null}, uses "UTF-8".
	 * @param includeStacktrace
	 *            If "true", includes the stacktrace of any Throwable in the generated JSON, defaults to "true".
	 * @return A JSON Layout.
	 *
	 * @deprecated Use {@link #newBuilder()} instead
	 */
	@Deprecated
	public static GsonLayout createLayout(
			final Configuration config,
			final boolean locationInfo,
			final boolean properties,
			final boolean propertiesAsList,
			final boolean complete,
			final boolean compact,
			final boolean eventEol,
			final String headerPattern,
			final String footerPattern,
			final Charset charset,
			final boolean includeStacktrace) {
		final boolean encodeThreadContextAsList = properties && propertiesAsList;
		return new GsonLayout(config, locationInfo, properties, encodeThreadContextAsList, complete, compact, eventEol,
				null, headerPattern, footerPattern, charset, includeStacktrace, false, false, false, null, false);
	}

	@PluginBuilderFactory
	public static <B extends Builder<B>> B newBuilder() {
		return new Builder<B>().asBuilder();
	}

	/**
	 * Creates a JSON Layout using the default settings. Useful for testing.
	 *
	 * @return A JSON Layout.
	 */
	public static GsonLayout createDefaultLayout() {
		return new GsonLayout(new DefaultConfiguration(), false, false, false, false, false, false, null,
				DEFAULT_HEADER, DEFAULT_FOOTER, StandardCharsets.UTF_8, true, false, false, false, null, false);
	}

	@Override
	public void toSerializable(final LogEvent event, final Writer writer) throws IOException {
		if (complete && eventCount > 0) {
			writer.append(", ");
		}
		super.toSerializable(event, writer);
	}

	private static ObjectWriter gson() {
		Map<Class<ITransferObject>,JsonClassWriter>[] customWriters0 = new Map[] { null };
		Map<Class<ITransferObject>,JsonClassWriter> customWriters = X.mapx(
			// see StackTraceElementMixIn
			StackTraceElement.class,new JsonClassWriter() {
				public void write(Object o, boolean showType, Writer output) throws IOException {
					StackTraceElement src = (StackTraceElement)o;
					output.write("\"class\":");
					JsonWriter.writeJsonUtf8String(src.getClassName(),output);
					output.write(",\"file\":");
					JsonWriter.writeJsonUtf8String(src.getFileName(),output);
					output.write(",\"method\":");
					JsonWriter.writeJsonUtf8String(src.getMethodName(),output);
					output.write(",\"line\":");
					output.write(Integer.toString(src.getLineNumber()));
				}
				public boolean hasPrimitiveForm() { return false; }
				public void writePrimitiveForm(Object o, Writer output) throws IOException {}
			},
			// see LevelMixIn
			Level.class,new JsonClassWriter() {
				public void write(Object o, boolean showType, Writer output) throws IOException {}
				public boolean hasPrimitiveForm() { return true; }
				public void writePrimitiveForm(Object o, Writer output) throws IOException {
					Level level = (Level)o;
					JsonWriter.writeJsonUtf8String(level.name(),output);
				}
			},
			// see MarkerMixIn
			Marker.class,new JsonClassWriter() {
				public void write(Object o, boolean showType, Writer output) throws IOException {}
				public boolean hasPrimitiveForm() { return true; }
				public void writePrimitiveForm(Object o, Writer output) throws IOException {
					Marker m = (Marker)o;
					Marker[] parents = m.getParents();
					if (parents==null || parents.length==0) {
						JsonWriter.writeJsonUtf8String(m.getName(),output);
					} else {
						output.write('[');
						JsonWriter.writeJsonUtf8String(m.getName(),output);
						for (Marker p : parents) {
							output.write(',');
							JsonWriter.writeJsonUtf8String(p.getName(),output);
						}
						output.write(']');
					}
				}
			},
			// see SimpleModuleInitializer -> MessageSerializer
			Message.class,new JsonClassWriter() {
				public void write(Object o, boolean showType, Writer output) throws IOException {}
				public boolean hasPrimitiveForm() { return true; }
				public void writePrimitiveForm(Object o, Writer output) throws IOException {
					Message m = (Message)o;
					JsonWriter.writeJsonUtf8String(m.getFormattedMessage(),output);
				}
			},
			// see SimpleModuleInitializer -> ObjectMessageSerializer
			ObjectMessage.class,new JsonClassWriter() {
				public void write(Object o, boolean showType, Writer output) throws IOException {}
				public boolean hasPrimitiveForm() { return true; }
				public void writePrimitiveForm(Object o, Writer output) throws IOException {
					ObjectMessage m = (ObjectMessage)o;
					String json = JsonWriter.objectToJson(m.getParameter(),X.mapx(
							JsonWriter.CUSTOM_WRITER_MAP,customWriters0[0],
							JsonWriter.TYPE,false));
					output.write(json);
				}
			},
			MementoMessage.class,new JsonClassWriter() {
				public void write(Object o, boolean showType, Writer output) throws IOException {}
				public boolean hasPrimitiveForm() { return true; }
				public void writePrimitiveForm(Object o, Writer output) throws IOException {
					MementoMessage m = (MementoMessage)o;
					String format = m.getFormat();
					Object[] params = m.getParameters();
					if (params==null || params.length==0) {
						if (format!=null) {
							JsonWriter.writeJsonUtf8String(format,output);
						} else output.write("null");
					} else {
						output.write('[');
						JsonWriter.writeJsonUtf8String(format,output);
						output.write(',');
						String json = JsonWriter.objectToJson(params,X.mapx(
								JsonWriter.CUSTOM_WRITER_MAP,customWriters0[0],
								JsonWriter.TYPE,false));
						output.write(json);
						output.write(']');
					}
				}
			},
			ITransferObject.class,new JsonClassWriter() {
				public void write(Object o, boolean showType, Writer output) throws IOException {}
				public boolean hasPrimitiveForm() { return true; }
				public void writePrimitiveForm(Object o, Writer output) throws IOException {
					ITransferObject t = (ITransferObject)o;
					JsonWriter.writeJsonUtf8String(t.getClass().getSimpleName()+"["+t.getId()+"]",output);
				}
			}
		);
		customWriters0[0] = customWriters;
		return new ObjectWriter() {
			public void toJson(Object event, Writer writer) throws IOException {
				JsonWriter.setAllowNanAndInfinity(true);
				String json = JsonWriter.objectToJson(event,X.mapx(
						JsonWriter.CUSTOM_WRITER_MAP,customWriters,
						JsonWriter.TYPE,false));
				writer.write(json);
			}
		};
	}

//	private static Gson gson() {
//		GsonBuilder gb = new GsonBuilder();
//		// see StackTraceElementMixIn
//		gb.registerTypeHierarchyAdapter(StackTraceElement.class,new JsonSerializer<StackTraceElement>() {
//			public JsonElement serialize(StackTraceElement src, Type type, JsonSerializationContext context) {
//				JsonObject o = new JsonObject();
//				o.addProperty("class",src.getClassName());
//				o.addProperty("file",src.getFileName());
//				o.addProperty("method",src.getMethodName());
//				o.addProperty("line",src.getLineNumber());
//				return o;
//			}
//		});
//		// see LevelMixIn
//		gb.registerTypeHierarchyAdapter(Level.class,new JsonSerializer<Level>() {
//			public JsonElement serialize(Level src, Type type, JsonSerializationContext context) {
//				return new JsonPrimitive(src.name());
//			}
//		});
//		// see MarkerMixIn
//		gb.registerTypeHierarchyAdapter(Marker.class,new JsonSerializer<Marker>() {
//			public JsonElement serialize(Marker src, Type type, JsonSerializationContext context) {
//				Marker[] parents = src.getParents();
//				if (parents==null || parents.length==0)
//					return new JsonPrimitive(src.getName());
//				JsonArray a = new JsonArray();
//				a.add(src.getName());
//				for (Marker p : parents)
//					a.add(p.getName());
//				return a;
//			}
//		});
//		// see SimpleModuleInitializer -> MessageSerializer
//		gb.registerTypeHierarchyAdapter(Message.class,new JsonSerializer<Message>() {
//			public JsonElement serialize(Message src, Type type, JsonSerializationContext context) {
//				return new JsonPrimitive(src.getFormattedMessage());
//			}
//		});
//		// see SimpleModuleInitializer -> ObjectMessageSerializer
//		gb.registerTypeHierarchyAdapter(ObjectMessage.class,new JsonSerializer<ObjectMessage>() {
//			public JsonElement serialize(ObjectMessage src, Type type, JsonSerializationContext context) {
//				return context.serialize(src.getParameter());
//			}
//		});
//		gb.registerTypeHierarchyAdapter(MementoMessage.class,new JsonSerializer<MementoMessage>() {
//			public JsonElement serialize(MementoMessage src, Type type, JsonSerializationContext context) {
//				Object[] params = src.getParameters();
//				if (params==null || params.length==0) {
//					String format = src.getFormat();
//					return format!=null ? new JsonPrimitive(format) : JsonNull.INSTANCE;
//				}
//				JsonArray a = new JsonArray();
//				a.add(src.getFormat());
//				a.add(context.serialize(params));
//				return a;
//			}
//		});
//		// TODO other serializers, see Initializers
//		gb.registerTypeHierarchyAdapter(Class.class,new JsonSerializer<Class>() {
//			public JsonElement serialize(Class src, Type type, JsonSerializationContext context) {
//				return new JsonPrimitive(src.getName());
//			}
//		});
//		gb.registerTypeHierarchyAdapter(ITransferObject.class,new JsonSerializer<ITransferObject>() {
//			public JsonElement serialize(ITransferObject src, Type type, JsonSerializationContext context) {
//				return new JsonPrimitive(src.getClass().getSimpleName()+"["+src.getId()+"]");
//			}
//		});
//		ExclusionStrategy ignoreIsSet = new ExclusionStrategy() {
//			public boolean shouldSkipField(FieldAttributes f) {
//				Class sc = f.getDeclaringClass().getSuperclass();
//				String cn = "de.scheller.transferobject.TransferObject2";
//				return "isSet".equals(f.getName()) && (sc!=null && cn.equals(sc.getName()) ||
//						sc.getSuperclass()!=null && cn.equals(sc.getSuperclass().getName()));
//			}
//			public boolean shouldSkipClass(Class<?> clazz) {
//				return false;
//			}
//		};
//		gb.addSerializationExclusionStrategy(ignoreIsSet);
//		gb.addDeserializationExclusionStrategy(ignoreIsSet);
//		return gson[0] = gb.create();
//	}
}
