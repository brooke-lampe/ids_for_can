/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.github.pires.obd.commands.protocol;

/**
 * Turns on line-feed.
 *
 */
public class LineFeedOnCommand extends ObdProtocolCommand {

    /**
     * <p>Constructor for LineFeedOnCommand.</p>
     */
    public LineFeedOnCommand() {
        super("AT L1");
    }

    /**
     * <p>Constructor for LineFeedOnCommand.</p>
     *
     * @param other a {@link com.github.pires.obd.commands.protocol.LineFeedOnCommand} object.
     */
    public LineFeedOnCommand(LineFeedOnCommand other) {
        super(other);
    }

    /** {@inheritDoc} */
    @Override
    public String getFormattedResult() {
        return getResult();
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return "Line Feed On";
    }
}