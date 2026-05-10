package de.scheller.fewo.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.Deflater;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.flipkart.zjsonpatch.JsonDiff;
import com.google.gson.Gson;

import de.scheller.platform.common.Base64;
import de.scheller.platform.network.web.UServer.WsLogic;

/**
 * @author kandzia
 */
public class SmallMessagesWsLogic implements WsLogic
{
	private final WsLogic logic;

	public SmallMessagesWsLogic(WsLogic logic) {
		this.logic = logic;
	}

	public void activate(Consumer send) {
		logic.activate(output -> {
//			System.out.println(">>> "+System.currentTimeMillis()+" "+output);
			String out = makeDiff(output);
//			String out = makeJson(output);
			if (out==null) return;
//			int len = Math.min(200,out.length());
//			System.out.println(">>> "+System.currentTimeMillis()+" "+out.length()+" chars: "+out.substring(0,len));
			send.accept(out);
		});
	}

	public void deactivate() {
		logic.deactivate();
	}

	public void receive(String message) {
		int len = Math.min(200,message.length());
		System.out.println("<<< "+System.currentTimeMillis()+" "+message.length()+" chars: "+message.substring(0,len));
		logic.receive(message);
	}

	public String makeJson(Object output) {
		return jackson().valueToTree(output).toString();
	}

	public JsonNode beforeNode;
	public String makeDiff(Object output) {
		JsonNode afterNode;
		try {
			ObjectMapper mapper = jackson();
			if (beforeNode==null)
				beforeNode = mapper.valueToTree(Collections.EMPTY_MAP);
			afterNode = mapper.valueToTree(output);
		} catch (Exception ex) {
			System.err.println("jackson failed, fallback to gson. reason: "+ex.getMessage());
			return new Gson().toJson(output);
		}

		boolean efficientDiff = false;
		int maxTokens = 1000;

		if (efficientDiff) try {
			JsonParser traverse = afterNode.traverse();
			int tokens = 0;
			while (traverse.nextToken()!=null) {
				tokens++;
				if (tokens>maxTokens) break;
			}
			efficientDiff &= tokens>maxTokens;
		} catch (Exception ignore) {}

		if (efficientDiff) try {
			JsonParser traverse = afterNode.traverse();
			int tokens = 0;
			while (traverse.nextToken()!=null) {
				tokens++;
				if (tokens>maxTokens) break;
			}
			efficientDiff &= tokens>maxTokens;
		} catch (Exception ignore) {}

		if (!efficientDiff && output instanceof Collection==false) {
			beforeNode = afterNode;
			return compress(afterNode.toString());
		}

		JsonNode patchNode = JsonDiff.asJson(beforeNode,afterNode);
		beforeNode = afterNode;
		return compress(patchNode.toString());
	}

	private String compress(String json) {
		if (!json.equals("[]")) {
			Deflater deflater = new Deflater();
			byte[] bytes = json.getBytes(StandardCharsets.ISO_8859_1);
			deflater.setInput(bytes);
			deflater.finish();
			ByteArrayOutputStream os = new ByteArrayOutputStream(bytes.length);
			byte[] buffer = new byte[4096];
			while (!deflater.finished()) {
				int count = deflater.deflate(buffer);
				os.write(buffer,0,count);
			}
			//os.close(); // close for ByteArrayOutputStream does nothing
			String out = "inflate,"+Base64.encodeToString(os.toByteArray(),false);
			return out.length()<json.length() ? out : json;
		} else return null;//afterNode.toString();
	}

	public static ObjectMapper jackson() {
		ObjectMapper m = new ObjectMapper();
		SimpleModule s = new SimpleModule();
		s.addSerializer(Double.class,new CustomDoubleSerializer());
		m.registerModule(s);
		return m;
	}

	public static class CustomDoubleSerializer extends JsonSerializer<Double> {
		DecimalFormat df = new DecimalFormat("#.###",DecimalFormatSymbols.getInstance(Locale.US));
		@Override
		public void serialize(Double value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonGenerationException {
			if (value==null)
				jgen.writeNull();
			else jgen.writeNumber(df.format(value));
		}
	}
}
