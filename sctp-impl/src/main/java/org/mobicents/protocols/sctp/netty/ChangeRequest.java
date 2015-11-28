/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */
package org.mobicents.protocols.sctp.netty;

import org.mobicents.protocols.api.Association;




/**
 * @author <a href="mailto:amit.bhayani@telestax.com">Amit Bhayani</a>
 * 
 */
public final class ChangeRequest {

    public static final int CONNECT = 0;

    private int type;
    private Association association;
    private long executionTime;

    /**
	 * 
	 */
    public ChangeRequest(Association association, int type, long executionTime) {
        this.association = association;
        this.type = type;
        this.executionTime = executionTime;
    }

    /**
     * @return the type
     */
    protected int getType() {
        return type;
    }

    /**
     * @return the esme
     */
    protected Association getAssociation() {
        return this.association;
    }

    /**
     * @return the executionTime
     */
    protected long getExecutionTime() {
        return executionTime;
    }

}
