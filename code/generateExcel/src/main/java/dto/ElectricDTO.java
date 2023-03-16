package dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * fileName:ElectricDTO
 * author:  charles
 * date:    2022/7/3
 * description:
 */
@Getter
@Setter
public class ElectricDTO {

	/**
	 * 电压
	 */
	private String voltage;

	/**
	 * 电流
	 */
	private List<String> electricCurrentList;
}
