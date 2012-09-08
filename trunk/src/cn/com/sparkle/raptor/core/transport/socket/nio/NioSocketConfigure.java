package cn.com.sparkle.raptor.core.transport.socket.nio;

public class NioSocketConfigure {
	private int revieveBuffSize = 1024;
	private int sentBuffSize = 1024;
	private boolean tcpNoDelay = true;
	private boolean keepAlive = true;
	private boolean oobInline;
	private Boolean reuseAddress = true;
	private int soLinger;
	private int trafficClass;
	private int processorNum = 1;
	private int registerWriteDelay = 10;
	private int registerReadDelay = 10;
	private int reRegisterWriteDelay = 300;
	private int registerConnecterDelay = 0;
	private int clearTimeoutSessionInterval = 300000;
	private int trySendNum = 60;
	private boolean isDaemon = false;
	
	
	
	
	public int getTrySendNum() {
		return trySendNum;
	}
	public void setTrySendNum(int trySendNum) {
		this.trySendNum = trySendNum;
	}
	public int getClearTimeoutSessionInterval() {
		return clearTimeoutSessionInterval;
	}
	public void setClearTimeoutSessionInterval(int clearTimeoutSessionInterval) {
		this.clearTimeoutSessionInterval = clearTimeoutSessionInterval;
	}
	public int getRegisterConnecterDelay() {
		return registerConnecterDelay;
	}
	public void setRegisterConnecterDelay(int registerConnecterDelay) {
		this.registerConnecterDelay = registerConnecterDelay;
	}
	public int getRegisterWriteDelay() {
		return registerWriteDelay;
	}
	public void setRegisterWriteDelay(int registerWriteDelay) {
		this.registerWriteDelay = registerWriteDelay;
	}
	public int getRegisterReadDelay() {
		return registerReadDelay;
	}
	public void setRegisterReadDelay(int registerReadDelay) {
		this.registerReadDelay = registerReadDelay;
	}
	
	public int getReRegisterWriteDelay() {
		return reRegisterWriteDelay;
	}
	public void setReRegisterWriteDelay(int reRegisterWriteDelay) {
		this.reRegisterWriteDelay = reRegisterWriteDelay;
	}
	public boolean isDaemon() {
		return isDaemon;
	}
	public void setDaemon(boolean isDaemon) {
		this.isDaemon = isDaemon;
	}
	public Integer getProcessorNum() {
        return processorNum;
    }
    public void setProcessorNum(Integer processorNum) {
        this.processorNum = processorNum;
    }
	public void setRevieveBuffSize(Integer revieveBuffSize) {
		this.revieveBuffSize = revieveBuffSize;
	}
	public void setSentBuffSize(Integer sentBuffSize) {
		this.sentBuffSize = sentBuffSize;
	}
	public void setTcpNoDelay(Boolean tcpNoDelay) {
		this.tcpNoDelay = tcpNoDelay;
	}
	public void setKeepAlive(Boolean keepAlive) {
		this.keepAlive = keepAlive;
	}
	public void setOobInline(Boolean oobInline) {
		this.oobInline = oobInline;
	}
	public void setReuseAddress(Boolean reuseAddress) {
		this.reuseAddress = reuseAddress;
	}
	public void setSoLinger(Integer soLinger) {
		this.soLinger = soLinger;
	}
	public void setTrafficClass(Integer trafficClass) {
		this.trafficClass = trafficClass;
	}
	public Integer getRevieveBuffSize() {
		return revieveBuffSize;
	}
	public void setRevieveBuffSize(int revieveBuffSize) {
		this.revieveBuffSize = Integer.valueOf(revieveBuffSize);
	}
	public Integer getSentBuffSize() {
		return sentBuffSize;
	}
	public void setSentBuffSize(int sentBuffSize) {
		this.sentBuffSize = Integer.valueOf(sentBuffSize);
	}
	public Boolean getTcpNoDelay() {
		return tcpNoDelay;
	}
	public void setTcpNoDelay(boolean tcpNoDelay) {
		this.tcpNoDelay = Boolean.valueOf(tcpNoDelay);
	}
	public Boolean getKeepAlive() {
		return keepAlive;
	}
	public void setKeepAlive(boolean keepAlive) {
		this.keepAlive = Boolean.valueOf(keepAlive);
	}
	public Boolean getOobInline() {
		return oobInline;
	}
	public void setOobInline(boolean oobInline) {
		this.oobInline = Boolean.valueOf(oobInline);
	}
	public Boolean getReuseAddress() {
		return reuseAddress;
	}
	public void setReuseAddress(boolean reuseAddress) {
		this.reuseAddress = Boolean.valueOf(reuseAddress);
	}
	public Integer getSoLinger() {
		return soLinger;
	}
	public void setSoLinger(int soLinger) {
		this.soLinger = Integer.valueOf(soLinger);
	}
	public Integer getTrafficClass() {
		return trafficClass;
	}
	public void setTrafficClass(int trafficClass) {
		this.trafficClass = Integer.valueOf(trafficClass);
	}
	
	
	
	
}	
