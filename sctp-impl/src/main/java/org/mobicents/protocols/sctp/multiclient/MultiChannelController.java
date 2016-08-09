package org.mobicents.protocols.sctp.multiclient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;

/**
 * Stores and manages OneToManyAssocMultiplexer and ManageableAssociation objects of a SCTP stack (MultiManagementImpl
 * instance).
 * 
 * @author balogh.gabor@alerant.hu
 *
 */
public class MultiChannelController {
    private static final Logger logger = Logger.getLogger(MultiChannelController.class);

    private MultiManagementImpl management;

    private final HashMap<Integer, ArrayList<OneToManyAssocMultiplexer>> multiplexers = new HashMap<Integer, ArrayList<OneToManyAssocMultiplexer>>();

    public MultiChannelController(MultiManagementImpl management) {
        super();
        this.management = management;
    }

    private synchronized OneToManyAssocMultiplexer findMultiplexerByHostAddrInfo(OneToManyAssociationImpl.HostAddressInfo hostAddressInfo) {
        OneToManyAssocMultiplexer ret = null;
        if (logger.isDebugEnabled()) {
            logger.debug("Number of multiplexers: " + multiplexers.size());
        }
        ArrayList<OneToManyAssocMultiplexer> mList = multiplexers.get(hostAddressInfo.getHostPort());
        if (mList == null || mList.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("No multiplexers found for local port: " + hostAddressInfo.getHostPort());
            }
        } else {
            for (OneToManyAssocMultiplexer am : mList) {
                if (am.getHostAddressInfo().matches(hostAddressInfo)) {
                    ret = am;
                }
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("findMultiplexerByHostAddr: " + hostAddressInfo + " returns: " + ret);
        }
        return ret;
    }

    private synchronized void storeMultiplexer(OneToManyAssociationImpl.HostAddressInfo hostAddrInfo,
            OneToManyAssocMultiplexer multiplexer) {
        ArrayList<OneToManyAssocMultiplexer> mList = multiplexers.get(hostAddrInfo.getHostPort());
        if (mList == null) {
            mList = new ArrayList<OneToManyAssocMultiplexer>();
            multiplexers.put(hostAddrInfo.getHostPort(), mList);
        }
        mList.add(multiplexer);
    }

    /**
     * Using the host address information of the given OneToManyAssociationImpl finds the appropriate multiplexer instance and
     * register it. If the multiplexer instance does not exists it is created by the method.
     *
     * @param assocImpl - OneToManyAssociation instance need to be registered to the appropriate OneToManyAssocMultiplexer
     * @return - the OneToManyAssocMultiplexer that is associated to the OneToManyAssociationImpl assocImpl
     * @throws IOException
     */
    protected synchronized OneToManyAssocMultiplexer register(ManageableAssociation assocImpl) throws IOException {
        if (assocImpl == null || assocImpl.getAssocInfo() == null || assocImpl.getAssocInfo().getHostInfo() == null) {
            logger.error("Unable to register association=" + assocImpl);
            return null;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("register: " + assocImpl);
        }
        OneToManyAssocMultiplexer ret = null;
            ret = findMultiplexerByHostAddrInfo(assocImpl.getAssocInfo().getHostInfo());
            if (ret == null) {
                ret = new OneToManyAssocMultiplexer(assocImpl.getAssocInfo().getHostInfo(), management);
                storeMultiplexer(assocImpl.getAssocInfo().getHostInfo(), ret);
            }
            ret.registerAssociation(assocImpl);
        return ret;
    }

    protected synchronized void stopAllMultiplexers() {
        for (List<OneToManyAssocMultiplexer> mList : multiplexers.values()) {
            for (OneToManyAssocMultiplexer multiplexer : mList) {
                try {
                    multiplexer.stop();
                } catch (IOException e) {
                    logger.warn(e);
                }
            }
        }
        multiplexers.clear();
    }

}
