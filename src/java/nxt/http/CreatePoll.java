package nxt.http;

import nxt.Account;
import nxt.Attachment;
import nxt.Attachment.MessagingPollCreation.PollBuilder;
import nxt.Constants;
import nxt.Nxt;
import nxt.NxtException;
import nxt.util.Convert;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

import static nxt.http.JSONResponses.INCORRECT_POLL_DESCRIPTION_LENGTH;
import static nxt.http.JSONResponses.INCORRECT_POLL_NAME_LENGTH;
import static nxt.http.JSONResponses.INCORRECT_POLL_OPTION_LENGTH;
import static nxt.http.JSONResponses.INCORRECT_ZEROOPTIONS;
import static nxt.http.JSONResponses.MISSING_DESCRIPTION;
import static nxt.http.JSONResponses.MISSING_NAME;

public final class CreatePoll extends CreateTransaction {

    static final CreatePoll instance = new CreatePoll();

    private CreatePoll() {
        super(new APITag[]{APITag.VS, APITag.CREATE_TRANSACTION},
                "name", "description", "finishHeight", "votingModel",
                "minNumberOfOptions", "maxNumberOfOptions",
                "minRangeValue", "maxRangeValue",
                "minBalance", "minBalanceModel", "holding",
                "option1", "option2", "option3");
    }

    @Override
    JSONStreamAware processRequest(HttpServletRequest req) throws NxtException {

        String nameValue = Convert.emptyToNull(req.getParameter("name"));
        String descriptionValue = req.getParameter("description");

        if (nameValue == null || nameValue.trim().isEmpty()) {
            return MISSING_NAME;
        } else if (descriptionValue == null) {
            return MISSING_DESCRIPTION;
        }

        if (nameValue.length() > Constants.MAX_POLL_NAME_LENGTH) {
            return INCORRECT_POLL_NAME_LENGTH;
        }

        if (descriptionValue.length() > Constants.MAX_POLL_DESCRIPTION_LENGTH) {
            return INCORRECT_POLL_DESCRIPTION_LENGTH;
        }

        List<String> options = new ArrayList<>();
        while (options.size() <= Constants.MAX_POLL_OPTION_COUNT) {
            String optionValue = Convert.emptyToNull(req.getParameter("option" + (options.size() + 1)));
            if (optionValue == null) {
                break;
            }
            if (optionValue.length() > Constants.MAX_POLL_OPTION_LENGTH || (optionValue = optionValue.trim()).isEmpty()) {
                return INCORRECT_POLL_OPTION_LENGTH;
            }
            options.add(optionValue);
        }

        byte optionsSize = (byte) options.size();
        if (options.size() == 0) {
            return INCORRECT_ZEROOPTIONS;
        }

        int currentHeight = Nxt.getBlockchain().getHeight();
        int finishHeight = ParameterParser.getInt(req, "finishHeight",
                currentHeight + Constants.VOTING_MIN_VOTE_DURATION,
                currentHeight + Constants.VOTING_MAX_VOTE_DURATION, true);

        byte votingModel = ParameterParser.getByte(req, "votingModel", Constants.VOTING_MODEL_NQT, Constants.VOTING_MODEL_CURRENCY, true);

        byte minNumberOfOptions = ParameterParser.getByte(req, "minNumberOfOptions", (byte) 1, optionsSize, true);
        byte maxNumberOfOptions = ParameterParser.getByte(req, "maxNumberOfOptions", minNumberOfOptions, optionsSize, true);

        byte minRangeValue = ParameterParser.getByte(req, "minRangeValue", Constants.VOTING_MIN_RANGE_VALUE_LIMIT, Constants.VOTING_MAX_RANGE_VALUE_LIMIT, true);
        byte maxRangeValue = ParameterParser.getByte(req, "maxRangeValue", minRangeValue, Constants.VOTING_MAX_RANGE_VALUE_LIMIT, true);

        PollBuilder builder = new PollBuilder(nameValue.trim(), descriptionValue.trim(),
                options.toArray(new String[options.size()]), finishHeight, votingModel,
                minNumberOfOptions, maxNumberOfOptions, minRangeValue, maxRangeValue);

        long minBalance = ParameterParser.getLong(req, "minBalance", 0, Long.MAX_VALUE, false);

        if (minBalance != 0) {
            byte minBalanceModel = ParameterParser.getByte(req, "minBalanceModel",
                    Constants.VOTING_MINBALANCE_NQT, Constants.VOTING_MINBALANCE_CURRENCY, true);
            builder.minBalance(minBalanceModel, minBalance);
        }

        long holdingId = ParameterParser.getLong(req, "holding", Long.MIN_VALUE, Long.MAX_VALUE, false);
        if (holdingId != 0) {
            builder.holdingId(holdingId);
        }

        Account account = ParameterParser.getSenderAccount(req);
        Attachment attachment = builder.build();
        return createTransaction(req, account, attachment);
    }
}
