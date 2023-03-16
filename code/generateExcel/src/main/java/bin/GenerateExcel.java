package bin;

import dto.ElectricDTO;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * fileName:GenerateExcel
 * author:  charles
 * date:    2022/7/2
 * description:按照txt数据生成excel文件
 */
public class GenerateExcel {

	private static ElectricDTO getElectricByFilePath(File file) throws Exception {
		if (file.getName().contains("info")) {
			return null;
		}
		ElectricDTO electric = new ElectricDTO();

		FileReader fw = new FileReader(file);
		BufferedReader br = new BufferedReader(fw);
		String content;
		String voltage = null;
		List<String> electricCurrentList = new ArrayList<String>();
		int num = 0;
		try {
			while ((content = br.readLine()) != null) {
				if (num < 3 || StringUtils.isEmpty(content)) {
					num++;
					continue;
				}
				String[] strArr = content.split("\t");
				voltage = strArr[2];
				electricCurrentList.add(strArr[3]);
			}
		} catch (Exception e) {
			System.out.println("读取数据出错，当前文件名称：" + file.getPath());
			throw e;
		}

		electric.setElectricCurrentList(electricCurrentList);
		electric.setVoltage(voltage);

		return electric;
	}

	private static List<ElectricDTO> getElectricList(File folder) throws Exception {
		List<ElectricDTO> electricDTOList = new ArrayList<ElectricDTO>();

		File[] fileList = folder.listFiles();
		if (fileList != null && fileList.length > 0) {
			for (int i = 0; i < fileList.length; i++) {
				File file = fileList[i];
				if (file != null) {
					if (file.isFile()) {
						ElectricDTO electric = getElectricByFilePath(file);
						if (electric != null) {
							electricDTOList.add(electric);
						}
					} else if (file.isDirectory()) {
						List<ElectricDTO> anoElectricDTOList = getElectricList(file);
						electricDTOList.addAll(anoElectricDTOList);
					}
				}
			}
		}
		return electricDTOList;
	}

	public static void generateExcel(String folderPath) throws Exception {
		if (StringUtils.isBlank(folderPath)) {
			throw new RuntimeException("文件夹路径不能为空！");
		}
		File folder = new File(folderPath);
		String excelName = "D:\\excel-";

		/*ExcelWriter excelWriter = EasyExcel.write(excelName,ElectricDTO.class).registerConverter(new ListConverter()).build();//*/
		//如果当前文件是文件夹，将其子文件也是文件夹的文件生成shell
		File[] fileArr = folder.listFiles();
		OutputStream os = null;
		XSSFWorkbook workbook = new XSSFWorkbook();
		if (fileArr != null && fileArr.length > 0) {
			for (int i = 0; i < fileArr.length; i++) {
				File file = fileArr[i];
				File excel = new File(excelName + file.getName() + ".xlsx");
				if (!excel.exists()) {
					excel.createNewFile();
				}
				os = new FileOutputStream(excel);
				Sheet sheet = workbook.createSheet(file.getName());
				List<ElectricDTO> electricDTOList = getElectricList(file);
				for (int j=0;j<electricDTOList.size();j++) {
					ElectricDTO electric = electricDTOList.get(j);
					Row row = sheet.getRow(j);
					if (row == null) {
						row = sheet.createRow(j);
					}
					Cell cell = row.createCell(0);
					cell.setCellValue(electric.getVoltage());
					for (int k=0;k<electric.getElectricCurrentList().size();k++) {
						row = sheet.getRow(k+1);
						if (row == null) {
							row = sheet.createRow(k+1);
						}
						Cell anoCell = row.createCell(j+1);
						anoCell.setCellValue(electric.getElectricCurrentList().get(k));
					}

				}
				workbook.write(os);
			}
		}
		workbook.close();
		os.close();
	}

	/*public static void generateExcel(String folderPath) throws Exception {
		if (StringUtils.isBlank(folderPath)) {
			throw new RuntimeException("文件夹路径不能为空！");
		}
		File folder = new File(folderPath);
		String excelName = "D:\\excel.xlsx";
		File excel = new File(excelName);
		if (!excel.exists()) {
			excel.createNewFile();
		}
		OutputStream os = new FileOutputStream(excelName);
		*//*ExcelWriter excelWriter = EasyExcel.write(excelName,ElectricDTO.class).registerConverter(new ListConverter()).build();//*//*
		//如果当前文件是文件夹，将其子文件也是文件夹的文件生成shell
		File[] fileArr = folder.listFiles();
		XSSFWorkbook workbook = null;
		if (fileArr != null && fileArr.length > 0) {
			for (int i = 0; i < fileArr.length; i++) {
				File file = fileArr[i];
				if (i == 0) {
					workbook = new XSSFWorkbook();
				} else {
					workbook = new XSSFWorkbook(excelName);
				}
				Sheet sheet = workbook.createSheet(file.getName());
				List<ElectricDTO> electricDTOList = getElectricList(file);
				for (int j=0;j<electricDTOList.size();j++) {
					ElectricDTO electric = electricDTOList.get(j);
					Row row = sheet.getRow(j);
					if (row == null) {
						row = sheet.createRow(j);
					}
					Cell cell = row.createCell(0);
					cell.setCellValue(electric.getVoltage());
					for (int k=0;k<electric.getElectricCurrentList().size();k++) {
						row = sheet.getRow(k+1);
						if (row == null) {
							row = sheet.createRow(k+1);
						}
						Cell anoCell = row.createCell(j+1);
						anoCell.setCellValue(electric.getElectricCurrentList().get(k));
					}
					workbook.write(os);
				}
			}
		}
		workbook.close();
		os.close();
	}*/

	public static void main(String[] args) throws Exception {
		generateExcel("D:\\file");
	}
}
