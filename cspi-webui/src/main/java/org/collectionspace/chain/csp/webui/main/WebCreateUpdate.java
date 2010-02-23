package org.collectionspace.chain.csp.webui.main;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.collectionspace.chain.csp.config.ConfigException;
import org.collectionspace.chain.csp.config.ReadOnlySection;
import org.collectionspace.chain.csp.config.Rules;
import org.collectionspace.chain.csp.schema.Record;
import org.collectionspace.chain.csp.schema.Spec;
import org.collectionspace.csp.api.persistence.ExistException;
import org.collectionspace.csp.api.persistence.Storage;
import org.collectionspace.csp.api.persistence.UnderlyingStorageException;
import org.collectionspace.csp.api.persistence.UnimplementedException;
import org.collectionspace.csp.api.ui.Operation;
import org.collectionspace.csp.api.ui.UIException;
import org.collectionspace.csp.api.ui.UIRequest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class WebCreateUpdate implements WebMethod {
	private String url_base,base;
	private boolean create;
	private Spec spec;
	
	public WebCreateUpdate(Record r,boolean create) { 
		spec=r.getSpec();
		this.url_base=r.getWebURL();
		this.base=r.getID();
		this.create=create;
	}
	
	// XXX refactor
	private String getResource(String in) throws IOException, JSONException {
		String path=getClass().getPackage().getName().replaceAll("\\.","/");
		InputStream stream=Thread.currentThread().getContextClassLoader().getResourceAsStream(path+"/"+in);
		System.err.println(path);
		String data=IOUtils.toString(stream);
		stream.close();		
		return data;
	}
	
	private void deleteAllRelations(Storage storage,String csid) throws JSONException, ExistException, UnimplementedException, UnderlyingStorageException {
		JSONObject r=new JSONObject();
		r.put("src",base+"/"+csid);		
		for(String relation : storage.getPaths("relations/main", r)) {
			storage.deleteJSON("relations/main/"+relation);
		}
	}
	
	private void setRelations(Storage storage,String csid,JSONArray relations) throws JSONException, ExistException, UnimplementedException, UnderlyingStorageException {
		deleteAllRelations(storage,csid);
		for(int i=0;i<relations.length();i++) {
			// Extract data from miniobject
			JSONObject in=relations.getJSONObject(i);
			String dst_type=spec.getRecordByWebUrl(in.getString("recordtype")).getID();
			String dst_id=in.getString("csid");
			String type=in.getString("relationshiptype");
			// Create relation
			JSONObject r=new JSONObject();
			r.put("src",base+"/"+csid);
			r.put("dst",dst_type+"/"+dst_id);
			r.put("type",type);
			storage.autocreateJSON("relations/main",r);
		}
	}
	
	private String xxx_mercury_search(Storage storage,String type,String value) throws ExistException, UnimplementedException, UnderlyingStorageException, JSONException {
		String target_base=spec.getRecordByWebUrl(type).getID();
		for(String path : storage.getPaths(target_base,null)) {
			JSONObject mini=storage.retrieveJSON(target_base+"/"+path+"/view");
			if(mini==null)
				return null;
			System.err.println("record is "+mini);
			if(mini.has("number") && mini.getString("number").equals(value))
				return path;
			
		}
		return null;
	}
	
	private String sendJSON(Storage storage,String path,JSONObject data) throws ExistException, UnimplementedException, UnderlyingStorageException, JSONException {
		JSONObject fields=data.optJSONObject("fields");
		JSONArray relations=data.optJSONArray("relations");
		if(path!=null) {
			// Update
			if(fields!=null)
				storage.updateJSON(base+"/"+path,fields);
		} else {
			// Create
			if(fields!=null)
				path=storage.autocreateJSON(base,fields);
		}
		if(relations!=null)
			setRelations(storage,path,relations);
		return path;
	}
	
	private void xxx_mercury_associate(Storage storage,String source_id,String type,String value) throws ExistException, UnimplementedException, UnderlyingStorageException, JSONException {
		String target_id=xxx_mercury_search(storage,type,value);
		if(target_id==null)
			return;		
		System.err.println("target id is "+target_id);
		JSONObject data=new JSONObject();
		data.put("src",base+"/"+source_id);
		data.put("dst",spec.getRecordByWebUrl(type).getID()+"/"+target_id);
		data.put("type","affects");
		storage.autocreateJSON("relations/main",data);
	}
	
	private void xxx_mercury_related_records(Storage storage,String id) throws IOException, JSONException, ExistException, UnimplementedException, UnderlyingStorageException {
		System.err.println("Applying related record hack to "+id);		
		String assocs=getResource("mercury-relations.txt");
		for(String line : assocs.split("\n")) {
			String[] data=line.split(" ");
			if(data.length<3)
				continue;
			if(data[0].equals(base))
				xxx_mercury_associate(storage,id,data[1],data[2]);
		}
	}
	
	private void store_set(Storage storage,UIRequest request,String path) throws UIException {
		try {
			JSONObject data=request.getJSONBody();
			if(create) {
				path=sendJSON(storage,null,data);
				xxx_mercury_related_records(storage,path);
			} else
				path=sendJSON(storage,path,data);
			if(path==null)
				throw new UIException("Insufficient data for create (no fields?)");
			data.put("csid",path);
			request.sendJSONResponse(data);
			request.setOperationPerformed(create?Operation.CREATE:Operation.UPDATE);
			request.setSecondaryRedirectPath(new String[]{url_base,path});
		} catch (JSONException x) {
			throw new UIException("Failed to parse json: "+x,x);
		} catch (ExistException x) {
			throw new UIException("Existence exception: "+x,x);
		} catch (UnimplementedException x) {
			throw new UIException("Unimplemented exception: "+x,x);
		} catch (UnderlyingStorageException x) {
			throw new UIException("Problem storing: "+x,x);
		} catch (IOException x) {
			throw new UIException("Problem storing: "+x,x);
		}
	}
	
	public void run(Object in, String[] tail) throws UIException {
		Request q=(Request)in;
		store_set(q.getStorage(),q.getUIRequest(),StringUtils.join(tail,"/"));
	}

	public void configure() throws ConfigException {}
	
	public void configure(WebUI ui,Spec spec) {}
}
