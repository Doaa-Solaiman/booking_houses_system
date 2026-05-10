package de.scheller.platform.network;

import java.util.HashMap;
import java.util.Map;

import de.scheller.platform.common.ItemStore.StoreItem;

/**
 * @author kandzia
 */
public class PlatformNodeConfigUpdates implements Network.Listener
{
	public static void install() {
		Network.listenToAll("event/changesCommitted","onCommittedChanges");
		Network.listener(new PlatformNodeConfigUpdates());
	}

	public void onMessage(String topic, Map<String,Object> data) {
		if ("onCommittedChanges".equals(topic)) {
			String[] parts = ((String)data.get("bodyAsString")).split("\\|");
			if ((parts.length-1)%3!=0)
				throw new IllegalArgumentException("wrong event format");
			PlatformNode.configs.setBatch(true);
			try {
				Map<String,StoreItem> itemsById = null;
				for (int i=1; i<parts.length;) {
					String ct = parts[i++];
					String ot = parts[i++];
					String id = parts[i++];
					if (ct==null || ot==null || id==null)
						throw new IllegalArgumentException("wrong event format");
					if (!ot.equals(PlatformNode.ConfigTable))
						continue;
					if (itemsById==null) {
						itemsById = new HashMap();
						for (StoreItem item : PlatformNode.configs.getItems())
							itemsById.put(item.meta(PlatformNode.ConfigId,String.class),item);
					}
					boolean success = PlatformNode.updateItem(itemsById.get(id));
					System.out.println("update config "+id+" "+success);
				}
			} finally {
				PlatformNode.configs.setBatch(false);
				PlatformNode.configs.flush();
			}
		}
	}
}
