package ieti.jobswipe.dto.chat;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRealtimeEventResponse {

    private String type;
    private Map<String, Object> payload;
}
