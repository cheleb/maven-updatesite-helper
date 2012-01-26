package org.eclipse.maven.mojo.updatesite.configuration;

/**
 * Site pojo.
 * 
 * @author chelebithil
 * 
 */
public class Site {

	/**
	 * Server id if m2 settings.
	 */
	private String serverId;

	/**
	 * The base of the update site.
	 */
	private String baseURL;

	/**
	 * Parent update site "/" separated.
	 */
	private String parent;

	/**
	 * Update site name.
	 */
	private String name;

	/**
	 * Getter for {@link Site#serverId}.
	 * 
	 * @return id of server
	 */
	public String getServerId() {
		return serverId;
	}

	/**
	 * Setter for {@link Site#serverId}.
	 * 
	 * @param serverId
	 */
	public void setServerId(String serverId) {
		this.serverId = serverId;
	}

	/**
	 * Getter for {@link Site#baseURL}.
	 * 
	 * @return baseURL
	 */
	public String getBaseURL() {
		return baseURL;
	}

	/**
	 * Setter for {@link Site#baseURL}.
	 * 
	 * @param baseURL
	 *            to set
	 */
	public void setBaseURL(String baseURL) {
		this.baseURL = baseURL;
	}

	/**
	 * Getter for {@link Site#parent}.
	 * 
	 * @return parent
	 */
	public String getParent() {
		return parent;
	}

	/**
	 * Setter for {@link Site#parent}
	 * 
	 * @param parent
	 */
	public void setParent(String parent) {
		this.parent = parent;
	}

	/**
	 * Getter for {@link Site#name}.
	 * 
	 * @return name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Setter for {@link Site#name}.
	 * 
	 * @param name
	 */
	public void setName(String name) {
		this.name = name;
	}

}
