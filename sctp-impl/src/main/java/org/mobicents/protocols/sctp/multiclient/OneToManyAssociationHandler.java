package org.mobicents.protocols.sctp.multiclient;

import java.io.IOException;
import java.net.SocketAddress;

import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

import com.sun.nio.sctp.AbstractNotificationHandler;
import com.sun.nio.sctp.AssociationChangeNotification;
import com.sun.nio.sctp.HandlerResult;
import com.sun.nio.sctp.Notification;
import com.sun.nio.sctp.PeerAddressChangeNotification;
import com.sun.nio.sctp.SendFailedNotification;
import com.sun.nio.sctp.ShutdownNotification;

public class OneToManyAssociationHandler extends AbstractNotificationHandler<OneToManyAssociationImpl> {

	private static final Logger logger = Logger.getLogger(OneToManyAssociationHandler.class);
	
	//Default value is 1 for TCP TODO what is it used for?
	private volatile int maxInboundStreams = 1;
	private volatile int maxOutboundStreams = 1;

	/**
	 * @param asscoitaion
	 */
	public OneToManyAssociationHandler() {

	}

	/**
	 * @return the maxInboundStreams
	 */
	public int getMaxInboundStreams() {
		return maxInboundStreams;
	}

	/**
	 * @return the maxOutboundStreams
	 */
	public int getMaxOutboundStreams() {
		return maxOutboundStreams;
	}
	
	@Override
	public HandlerResult handleNotification(Notification arg0, 	OneToManyAssociationImpl arg1) {
		if (arg0 instanceof AssociationChangeNotification) {
			return handleNotification((AssociationChangeNotification) arg0, arg1);
		}
		if (arg0 instanceof ShutdownNotification) {
			return handleNotification((ShutdownNotification) arg0, arg1);			
		}
		if (arg0 instanceof SendFailedNotification) {
			return handleNotification((SendFailedNotification) arg0, arg1);			
		}
		if (arg0 instanceof PeerAddressChangeNotification) {
			return handleNotification((PeerAddressChangeNotification) arg0, arg1);			
		}	
		logger.warn("Polymorphism failure: "+arg0+" arg1: "+arg1);
		return super.handleNotification(arg0, arg1);		
	}
	
	@Override
	public HandlerResult handleNotification(AssociationChangeNotification not, OneToManyAssociationImpl associtaion) {
		
		switch (not.event()) {
		case COMM_UP:
			if (not.association() != null) {
				this.maxOutboundStreams = not.association().maxOutboundStreams();
				this.maxInboundStreams = not.association().maxInboundStreams();
			}

			if (logger.isInfoEnabled()) {
				logger.info(String.format("New association setup for Association=%s with %d outbound streams, and %d inbound streams, sctp assoc is %s.\n",
						associtaion.getName(), this.maxOutboundStreams, this.maxInboundStreams, not.association()));
			}

			associtaion.createworkerThreadTable(Math.max(this.maxInboundStreams, this.maxOutboundStreams));

			// TODO assign Thread's ?
			try {
				associtaion.markAssociationUp();
				associtaion.getAssociationListener().onCommunicationUp(associtaion, this.maxInboundStreams, this.maxOutboundStreams);
			} catch (Exception e) {
				logger.error(String.format("Exception while calling onCommunicationUp on AssociationListener for Association=%s", associtaion.getName()), e);
			}
			return HandlerResult.CONTINUE;

		case CANT_START:
			logger.error(String.format("Can't start for Association=%s", associtaion.getName()));
			return HandlerResult.CONTINUE;
		case COMM_LOST:
			logger.warn(String.format("Communication lost for Association=%s", associtaion.getName()));

			// Close the Socket
			/*TODO mark for delete
			 * associtaion.close();

			associtaion.scheduleConnect();*/
			try {
				associtaion.markAssociationDown();
				associtaion.getAssociationListener().onCommunicationLost(associtaion);
			} catch (Exception e) {
				logger.error(String.format("Exception while calling onCommunicationLost on AssociationListener for Association=%s", associtaion.getName()), e);
			}
			return HandlerResult.RETURN;
		case RESTART:
			logger.warn(String.format("Restart for Association=%s", associtaion.getName()));
			try {
				associtaion.getAssociationListener().onCommunicationRestart(associtaion);
			} catch (Exception e) {
				logger.error(String.format("Exception while calling onCommunicationRestart on AssociationListener for Association=%s", associtaion.getName()),
						e);
			}
			return HandlerResult.CONTINUE;
		case SHUTDOWN:
			if (logger.isInfoEnabled()) {
				logger.info(String.format("Shutdown for Association=%s", associtaion.getName()));
			}
			try {
				associtaion.markAssociationDown();
				associtaion.getAssociationListener().onCommunicationShutdown(associtaion);
			} catch (Exception e) {
				logger.error(String.format("Exception while calling onCommunicationShutdown on AssociationListener for Association=%s", associtaion.getName()),
						e);
			}
			return HandlerResult.RETURN;
		default:
			logger.warn(String.format("Received unkown Event=%s for Association=%s", not.event(), associtaion.getName()));
			break;
		}

		return HandlerResult.CONTINUE;
	}

	@Override
	public HandlerResult handleNotification(ShutdownNotification not, OneToManyAssociationImpl associtaion) {
		if (logger.isInfoEnabled()) {
			logger.info(String.format("Association=%s SHUTDOWN", associtaion.getName()));
		}

		// TODO assign Thread's ?

		try {
			associtaion.markAssociationDown();
			associtaion.getAssociationListener().onCommunicationShutdown(associtaion);
		} catch (Exception e) {
			logger.error(String.format("Exception while calling onCommunicationShutdown on AssociationListener for Association=%s", associtaion.getName()), e);
		}

		return HandlerResult.RETURN;
	}

	@Override
	public HandlerResult handleNotification(SendFailedNotification notification, OneToManyAssociationImpl associtaion) {
//        logger.error(String.format("Association=%s SendFailedNotification", associtaion.getName()));
        logger.error(String.format("Association=" + associtaion.getName() + " SendFailedNotification, errorCode=" + notification.errorCode()));
		return HandlerResult.RETURN;
	}

	@Override
	public  HandlerResult handleNotification(PeerAddressChangeNotification notification, OneToManyAssociationImpl associtaion) {
		//associtaion.peerSocketAddress = notification.address();
		if(logger.isEnabledFor(Priority.WARN)){
			logger.warn(String.format("Peer Address changed to=%s for Association=%s", notification.address(), associtaion.getName()));
		}
		return HandlerResult.CONTINUE;
	}
}
